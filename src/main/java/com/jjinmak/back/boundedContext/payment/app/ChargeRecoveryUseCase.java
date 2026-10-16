package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.exception.PgApiException;
import com.jjinmak.back.boundedContext.payment.out.ChargeRepository;
import com.jjinmak.back.boundedContext.payment.out.TossPaymentsClient;
import com.jjinmak.back.boundedContext.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static com.jjinmak.back.boundedContext.payment.domain.enums.ChargePurpose.FOR_PAYMENT;
import static com.jjinmak.back.boundedContext.payment.domain.enums.ChargeStatus.CHARGED;
import static com.jjinmak.back.boundedContext.payment.domain.enums.ChargeStatus.REQUESTED;
import static com.jjinmak.back.boundedContext.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType.CHARGE;
import static com.jjinmak.back.boundedContext.payment.exception.PaymentErrorCode.CHARGE_NOT_FOUND;
import static com.jjinmak.back.boundedContext.payment.out.TossPaymentsClient.NOT_FOUND_PAYMENT;

/**
 * REQUESTED 에 너무 오래 머문 Charge 정리. (PaymentScheduler → PaymentFacade → 여기)
 *
 * 토스에 pgOrderId 로 물어봐서
 *  - 결제 기록이 없음(NOT_FOUND_PAYMENT) / ABORTED / EXPIRED / CANCELED / READY / IN_PROGRESS → ABORTED 로 닫는다
 *  - DONE (승인은 됐는데 우리 승인 단계가 끝나지 못한 것)
 *      · MANUAL      → 사용자가 낸 돈이므로 지갑에 반영해 CHARGED 로 (돈을 잃지 않게)
 *      · FOR_PAYMENT → 주문 흐름은 이미 깨졌으므로 토스 취소 후 ABORTED (카드로 환불)
 *
 * 토스 인증 유효 시간(승인 가능 시간)은 10~30분이라, 임계값은 그보다 넉넉히 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeRecoveryUseCase {

    public static final Duration STALE_AFTER = Duration.ofMinutes(40);
    private static final String CANCEL_KEY_PREFIX = "charge-recover-cancel-";

    private final ChargeRepository chargeRepository;
    private final WalletSupport walletSupport;
    private final LedgerRecorder ledgerRecorder;
    private final TossPaymentsClient toss;

    /** REQUESTED 에 너무 오래 머문 건의 id */
    public List<Long> findStaleRequestedIds() {
        return chargeRepository.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                        REQUESTED, LocalDateTime.now().minus(STALE_AFTER))
                .stream().map(Charge::getId).toList();
    }

    /** 있으면 안 되는 상태. 홀딩과 같은 트랜잭션에서 USED 가 돼야 하므로 여기 걸리면 버그다 */
    public List<Long> findChargedButUnusedIds() {
        return chargeRepository.findTop100ByPurposeAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                        FOR_PAYMENT, CHARGED, LocalDateTime.now().minus(STALE_AFTER))
                .stream().map(Charge::getId).toList();
    }

    public void recover(Long chargeId) {
        Charge charge = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new BusinessException(CHARGE_NOT_FOUND));

        if (charge.getStatus() != REQUESTED) return;   // 조회 후 그 사이에 처리된 건

        TossPaymentResponse remote;
        try {
            remote = toss.findByOrderId(charge.getPgOrderId());
        } catch (PgApiException e) {
            if (e.isCode(NOT_FOUND_PAYMENT)) {
                charge.markAborted(LocalDateTime.now(), NOT_FOUND_PAYMENT, "결제창에서 결제가 완료되지 않음");
                return;
            }
            throw e;   // 네트워크 오류 등: 다음 주기에 재시도
        }

        LocalDateTime now = LocalDateTime.now();

        if (remote.isDone()) {
            if (charge.isForPayment()) {
                toss.cancel(remote.paymentKey(), "미완료 주문 결제 자동 취소", CANCEL_KEY_PREFIX + chargeId);
                charge.markAborted(now, "RECOVERED_CANCELED", "승인 후 주문 결제가 완료되지 않아 취소");
            } else {
                Wallet wallet = walletSupport.getWalletByHolderId(charge.getHolderId());
                ledgerRecorder.deposit(wallet, CHARGE, charge.getAmount(), PAYMENT, chargeId);
                charge.markCharged(remote.paymentKey(), remote.approvedAtLocal(), now);
            }
            log.info("REQUESTED 충전 복구: chargeId={} purpose={} tossStatus=DONE", chargeId, charge.getPurpose());
            return;
        }

        charge.markAborted(now, remote.status(), remote.failureMessage());
        log.info("REQUESTED 충전 정리: chargeId={} tossStatus={}", chargeId, remote.status());
    }
}
