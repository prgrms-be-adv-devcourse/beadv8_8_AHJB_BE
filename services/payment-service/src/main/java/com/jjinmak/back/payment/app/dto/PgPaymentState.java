package com.jjinmak.back.payment.app.dto;

import com.jjinmak.back.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.payment.out.dto.TossTransactionResponse;

import java.util.List;

/**
 * 토스 쪽 결제 1건(주문번호 단위)의 상태. 대사에서 Charge 와 대조하기 위한 값이며 저장하지 않는다.
 *
 * 토스 거래는 승인 1줄, 취소 1줄이 따로 오고 Charge 는 상태 하나라서 그대로는 비교할 수 없다.
 * 그래서 같은 주문번호의 거래를 모아 "지금 토스에 돈이 얼마 남아 있는가" 로 접는다.
 *
 * - approvedAmount : 승인된 금액
 * - balanceAmount  : 토스에 남아 있는 금액 (승인 - 취소). 전액 취소면 0
 *
 * - fromTransactions : 대상일의 거래 목록(GET /v1/transactions)에서. 그날 거래만 보므로 UNKNOWN 이 나올 수 있다
 * - fromPayment      : 단건 조회(GET /v1/payments/orders/{orderId})에서. 현재 상태라 UNKNOWN 이 나오지 않는다
 */
public record PgPaymentState(
        String pgOrderId,
        String paymentKey,
        Kind kind,
        long approvedAmount,
        long balanceAmount
) {

    /**
     * NONE             : 토스에서 돈이 움직이지 않음 (거래가 없거나, 승인되지 않고 끝난 결제)
     * APPROVED         : 승인만 됨
     * CANCELED         : 승인 후 전액 취소됨
     * PARTIAL_CANCELED : 일부만 취소됨 (지금 정책에는 부분 취소가 없어 나오면 안 되는 상태)
     * UNKNOWN          : 그날 거래만으로는 알 수 없음 (승인 없이 취소만 있음 = 승인이 다른 날). 단건 조회로 확정해야 한다
     */
    public enum Kind {
        NONE,
        APPROVED,
        CANCELED,
        PARTIAL_CANCELED,
        UNKNOWN
    }

    // 토스 거래·결제의 status 값
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_CANCELED = "CANCELED";
    private static final String STATUS_PARTIAL_CANCELED = "PARTIAL_CANCELED";

    // 토스에 거래가 없는 주문번호
    public static PgPaymentState none(String pgOrderId) {
        return new PgPaymentState(pgOrderId, null, Kind.NONE, 0L, 0L);
    }

    /**
     * 같은 주문번호의 그날 거래들로 상태를 만든다.
     *
     * 승인 줄(DONE)과 취소 줄(CANCELED / PARTIAL_CANCELED)을 status 로 나눠 각각 합한다.
     * 취소 줄의 금액도 양수로 오므로 금액을 그대로 더하면 안 된다. (부호가 바뀌어도 되도록 절댓값으로 계산)
     * 그 외 status 의 줄은 돈이 움직이지 않은 것이라 무시한다.
     */
    public static PgPaymentState fromTransactions(String pgOrderId, List<TossTransactionResponse> transactions) {
        long approved = 0L;
        long canceled = 0L;
        String paymentKey = null;

        if (transactions != null) {
            for (TossTransactionResponse transaction : transactions) {
                long amount = Math.abs(transaction.amount());
                if (STATUS_DONE.equals(transaction.status())) {
                    approved += amount;
                } else if (isCancelStatus(transaction.status())) {
                    canceled += amount;
                } else {
                    continue;
                }
                if (paymentKey == null) {
                    paymentKey = transaction.paymentKey();
                }
            }
        }

        if (approved == 0L && canceled == 0L) {
            return none(pgOrderId);
        }
        // 승인 줄 없이 취소만 있음: 승인이 다른 날이라 남은 금액을 알 수 없다
        if (approved == 0L) {
            return new PgPaymentState(pgOrderId, paymentKey, Kind.UNKNOWN, 0L, 0L);
        }
        if (canceled == 0L) {
            return new PgPaymentState(pgOrderId, paymentKey, Kind.APPROVED, approved, approved);
        }
        if (canceled >= approved) {
            return new PgPaymentState(pgOrderId, paymentKey, Kind.CANCELED, approved, 0L);
        }
        return new PgPaymentState(pgOrderId, paymentKey, Kind.PARTIAL_CANCELED, approved, approved - canceled);
    }

    /**
     * 단건 조회 결과로 상태를 만든다. 토스가 알려주는 현재 상태(status)와 남은 금액(balanceAmount)을 그대로 쓴다.
     * 승인되지 않은 결제(READY, IN_PROGRESS, WAITING_FOR_DEPOSIT, ABORTED, EXPIRED)는 돈이 움직이지 않았으므로 NONE.
     */
    public static PgPaymentState fromPayment(TossPaymentResponse payment) {
        String status = payment.status();
        if (STATUS_DONE.equals(status)) {
            return of(payment, Kind.APPROVED);
        }
        if (STATUS_CANCELED.equals(status)) {
            return of(payment, Kind.CANCELED);
        }
        if (STATUS_PARTIAL_CANCELED.equals(status)) {
            return of(payment, Kind.PARTIAL_CANCELED);
        }
        return none(payment.orderId());
    }

    // 그날 거래만으로는 판단할 수 없어 단건 조회가 필요한지
    public boolean isUnknown() {
        return kind == Kind.UNKNOWN;
    }

    private static PgPaymentState of(TossPaymentResponse payment, Kind kind) {
        return new PgPaymentState(payment.orderId(), payment.paymentKey(), kind,
                payment.totalAmount(), payment.balanceAmount());
    }

    private static boolean isCancelStatus(String status) {
        return STATUS_CANCELED.equals(status) || STATUS_PARTIAL_CANCELED.equals(status);
    }
}
