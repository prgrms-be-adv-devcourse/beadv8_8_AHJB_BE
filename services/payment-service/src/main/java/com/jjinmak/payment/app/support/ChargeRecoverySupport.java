package com.jjinmak.payment.app.support;

import com.jjinmak.payment.domain.Charge;
import com.jjinmak.payment.domain.Wallet;
import com.jjinmak.payment.out.TossPaymentsClient;
import com.jjinmak.payment.out.dto.TossPaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.jjinmak.payment.domain.enums.ChargeStatus.REQUESTED;
import static com.jjinmak.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.payment.domain.enums.WalletTransactionType.CHARGE;
import static com.jjinmak.payment.out.TossPaymentsClient.NOT_FOUND_PAYMENT;

/**
 * 승인 전(REQUESTED) 충전 건을 토스 상태에 맞추는 복구 판단. (호출하는 쪽 트랜잭션 안에서 실행)
 *
 * 충전 복구 스케줄러(40분 지난 건)와 토스 웹훅 처리가 같은 정책을 쓰도록 한 곳에 모았다.
 * 토스 조회는 호출하는 쪽이 하고(스케줄러는 orderId, 웹훅은 paymentKey), 여기서는 조회 결과를 받아 반영만 한다.
 *
 *  - 토스에 결제 기록이 없음 → ABORTED (결제창에서 결제하지 않음)
 *  - 토스 DONE (승인은 됐는데 우리 승인 단계가 끝나지 못한 것)
 *      · MANUAL      → 사용자가 낸 돈이므로 지갑에 반영해 CHARGED (돈을 잃지 않게)
 *      · FOR_PAYMENT → 주문 흐름은 이미 깨졌으므로 토스 취소 후 ABORTED (카드로 환불)
 *  - 그 외 (ABORTED / EXPIRED / CANCELED / READY / IN_PROGRESS) → ABORTED
 *
 * 주의: READY / IN_PROGRESS 도 닫으므로, 사용자가 아직 결제 중이거나 우리 승인 요청이 진행 중일 수 있는
 * 최근 건에 호출하면 정상 결제를 망가뜨린다. 호출하는 쪽이 충분히 시간이 지난 건에만 호출해야 한다.
 *
 * 토스 취소 Idempotency-Key 를 두 경로가 같이 쓰므로, 스케줄러와 웹훅이 같은 건을 동시에 취소해도 토스 취소는 한 번만 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChargeRecoverySupport {

    /** 토스 Idempotency-Key 접두사. 뒤에 pgOrderId 를 붙인다 (DB id 는 초기화 시 중복됨 — PgApprovalSupport 참고) */
    private static final String CANCEL_KEY_PREFIX = "charge-recover-cancel-";

    private final WalletSupport walletSupport;
    private final LedgerRecorder ledgerRecorder;
    private final TossPaymentsClient toss;

    /**
     * 토스에 결제 기록이 없는 건 닫기.
     * @return 반영했으면 true, 이미 REQUESTED 가 아니라 할 일이 없으면 false
     */
    public boolean closeNotFound(Charge charge) {
        if (charge.getStatus() != REQUESTED) return false;

        charge.markAborted(LocalDateTime.now(), NOT_FOUND_PAYMENT, "결제창에서 결제가 완료되지 않음");
        log.info("REQUESTED 충전 정리: chargeId={} tossStatus={}", charge.getId(), NOT_FOUND_PAYMENT);
        return true;
    }

    /**
     * 토스 조회 결과로 REQUESTED 충전 건 반영.
     * @return 반영했으면 true, 이미 REQUESTED 가 아니라 할 일이 없으면 false
     */
    public boolean recover(Charge charge, TossPaymentResponse remote) {
        if (charge.getStatus() != REQUESTED) return false;

        LocalDateTime now = LocalDateTime.now();

        if (remote.isDone()) {
            if (charge.isForPayment()) {
                toss.cancel(remote.paymentKey(), "미완료 주문 결제 자동 취소", CANCEL_KEY_PREFIX + charge.getPgOrderId());
                charge.markAborted(now, "RECOVERED_CANCELED", "승인 후 주문 결제가 완료되지 않아 취소");
            } else {
                Wallet wallet = walletSupport.getWalletByHolderId(charge.getHolderId());
                ledgerRecorder.deposit(wallet, CHARGE, charge.getAmount(), PAYMENT, charge.getId());
                charge.markCharged(remote.paymentKey(), remote.approvedAtLocal(), now, remote.method(), remote.receiptUrl());
            }
            log.info("REQUESTED 충전 복구: chargeId={} purpose={} tossStatus=DONE", charge.getId(), charge.getPurpose());
            return true;
        }

        charge.markAborted(now, remote.status(), remote.failureMessage());
        log.info("REQUESTED 충전 정리: chargeId={} tossStatus={}", charge.getId(), remote.status());
        return true;
    }
}
