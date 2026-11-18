package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.payment.app.dto.DetectedMismatchDto;
import com.jjinmak.back.payment.app.dto.PgMatchResult;
import com.jjinmak.back.payment.app.dto.PgPaymentState;
import com.jjinmak.back.payment.app.dto.PgReconciliationResult;
import com.jjinmak.back.payment.app.support.PgReconciliationMatcher;
import com.jjinmak.back.payment.app.support.PgTransactionCollector;
import com.jjinmak.back.payment.app.support.ReconciliationRunRecorder;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.enums.MismatchTargetType;
import com.jjinmak.back.payment.domain.enums.MismatchType;
import com.jjinmak.back.payment.domain.enums.ReconciliationType;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * PG 대사: 대상일의 토스 거래와 충전 건(Charge)을 맞춰 본다. (ReconciliationScheduler → ReconciliationFacade → ReconciliationPgUseCase)
 *
 *   start      (트랜잭션)      실행 기록을 만들거나 다시 연다
 *   reconcile  (트랜잭션 없음)  토스 거래 수집 → 충전 건 조회 → 1차 판정 → 불일치 후보만 토스 단건 조회로 2차 확정
 *   finish     (트랜잭션)      확정된 불일치를 저장하고 실행을 닫는다
 *   fail       (트랜잭션)      도중에 예외가 났을 때 실행을 FAILED 로 닫는다
 *
 * reconcile 은 토스를 여러 번 호출하므로 DB 트랜잭션 밖에서 실행한다. DB 는 읽기만 하고 쓰기는 finish 에서 한 번에 한다.
 * 불일치를 발견해도 돈이나 상태를 고치지 않는다. 기록만 하고 정정은 운영자가 한다.
 */
@Service
@RequiredArgsConstructor
public class ReconciliationPgUseCase {

    // 주문번호 목록으로 충전 건을 조회할 때 한 번에 넘기는 개수 (IN 절이 너무 길어지지 않게 나눠서 조회)
    static final int CHARGE_QUERY_CHUNK = 500;

    private final PgTransactionCollector pgTransactionCollector;
    private final PgReconciliationMatcher pgReconciliationMatcher;
    private final ReconciliationRunRecorder reconciliationRunRecorder;
    private final ChargeRepository chargeRepository;

    // 대사 시작. 진행할 실행의 id 를 돌려주고, 이미 끝났거나 실행 중이면 빈 값
    public Optional<Long> start(LocalDate targetDate) {
        return reconciliationRunRecorder.start(ReconciliationType.PG, targetDate);
    }

    /**
     * 대상일의 토스 거래와 충전 건을 대조한다. 저장은 하지 않고 결과만 돌려준다.
     *
     * 1차 판정은 그날의 거래 목록으로 한다. 그날 목록은 그 결제의 전체 이력이 아닐 수 있어서
     * (승인은 대상일, 취소는 다음 날 새벽 / 승인은 전날, 취소만 대상일) 1차에서 일치하지 않은 건은
     * 토스 단건 조회로 현재 상태를 받고 충전 건도 다시 읽어 한 번 더 대조한다. 그래도 불일치인 건만 결과에 담는다.
     */
    public PgReconciliationResult reconcile(LocalDate targetDate) {
        Map<String, PgPaymentState> tossStates = pgTransactionCollector.collect(targetDate);
        Map<String, Charge> charges = findCharges(tossStates.keySet(), targetDate);

        // 대조 대상 = 토스 거래에 나온 주문번호 + 우리만 승인·취소로 알고 있는 충전 건의 주문번호
        Set<String> pgOrderIds = new LinkedHashSet<>(tossStates.keySet());
        pgOrderIds.addAll(charges.keySet());

        List<DetectedMismatchDto> mismatches = new ArrayList<>();
        for (String pgOrderId : pgOrderIds) {
            PgPaymentState toss = tossStates.getOrDefault(pgOrderId, PgPaymentState.none(pgOrderId));

            // 1차 판정: 그날 거래 기준
            if (pgReconciliationMatcher.match(toss, charges.get(pgOrderId)).isMatched()) {
                continue;
            }

            // 2차 확정: 토스 현재 상태와 최신 충전 건 기준
            PgPaymentState current = pgTransactionCollector.fetchCurrent(pgOrderId);
            Charge charge = chargeRepository.findByPgOrderId(pgOrderId).orElse(null);
            PgMatchResult confirmed = pgReconciliationMatcher.match(current, charge);
            if (confirmed.isMismatched()) {
                mismatches.add(toMismatch(confirmed.mismatchType(), current, charge));
            }
        }

        return new PgReconciliationResult(pgOrderIds.size(), mismatches);
    }

    // 대사 종료. 확정된 불일치를 저장하고 실행을 MATCHED / MISMATCHED 로 닫는다
    public void finish(Long runId, PgReconciliationResult result) {
        reconciliationRunRecorder.finish(runId, result.checkedCount(), result.mismatches());
    }

    // 대사를 끝내지 못함. 실행을 FAILED 로 닫는다
    public void fail(Long runId, String reason) {
        reconciliationRunRecorder.fail(runId, reason);
    }

    /**
     * 대조할 충전 건을 주문번호로 찾을 수 있게 모은다.
     *  1. 토스 거래에 나온 주문번호의 충전 건
     *  2. 대상일에 승인됐거나 취소된 충전 건 — 토스 목록에는 없는데 우리만 승인·취소로 알고 있는 건을 잡기 위함
     */
    private Map<String, Charge> findCharges(Set<String> tossOrderIds, LocalDate targetDate) {
        Map<String, Charge> charges = new LinkedHashMap<>();

        List<String> orderIds = new ArrayList<>(tossOrderIds);
        for (int from = 0; from < orderIds.size(); from += CHARGE_QUERY_CHUNK) {
            List<String> chunk = orderIds.subList(from, Math.min(from + CHARGE_QUERY_CHUNK, orderIds.size()));
            for (Charge charge : chargeRepository.findByPgOrderIdIn(chunk)) {
                charges.put(charge.getPgOrderId(), charge);
            }
        }

        List<Charge> changedOnTargetDate = chargeRepository.findApprovedOrCanceledBetween(
                targetDate.atStartOfDay(), targetDate.plusDays(1).atStartOfDay());
        for (Charge charge : changedOnTargetDate) {
            charges.putIfAbsent(charge.getPgOrderId(), charge);
        }
        return charges;
    }

    /**
     * 확정된 불일치를 기록용 값으로 만든다. 기준(expected)은 토스, 우리 값(actual)은 충전 건이다.
     *  - expectedAmount : 토스에 남아 있는 금액
     *  - actualAmount   : 우리가 예치금으로 인정하고 있는 금액 (CHARGED · USED 면 충전 금액, 그 외는 0)
     * 충전 건이 없으면(PG_ONLY) 대상을 토스 주문번호로 가리키고 우리 쪽 값은 비운다.
     */
    private DetectedMismatchDto toMismatch(MismatchType type, PgPaymentState toss, Charge charge) {
        if (charge == null) {
            return new DetectedMismatchDto(type, MismatchTargetType.PG_TRANSACTION, toss.pgOrderId(), toss.pgOrderId(),
                    toss.kind().name(), toss.balanceAmount(), null, null, detailOf(type, toss, null));
        }
        long creditedAmount = charge.isCharged() ? charge.getAmount() : 0L;
        return new DetectedMismatchDto(type, MismatchTargetType.CHARGE, String.valueOf(charge.getId()), charge.getPgOrderId(),
                toss.kind().name(), toss.balanceAmount(), charge.getStatus().name(), creditedAmount,
                detailOf(type, toss, charge));
    }

    // 사람이 읽을 설명. 예: 토스는 취소됐는데 충전 건에 반영되지 않음. 토스 승인 100000원, 토스에 남은 금액 0원, 충전 금액 100000원
    private String detailOf(MismatchType type, PgPaymentState toss, Charge charge) {
        StringBuilder detail = new StringBuilder(type.getDescription())
                .append(". 토스 승인 ").append(toss.approvedAmount()).append("원")
                .append(", 토스에 남은 금액 ").append(toss.balanceAmount()).append("원");
        if (charge != null) {
            detail.append(", 충전 금액 ").append(charge.getAmount()).append("원");
        }
        return detail.toString();
    }
}
