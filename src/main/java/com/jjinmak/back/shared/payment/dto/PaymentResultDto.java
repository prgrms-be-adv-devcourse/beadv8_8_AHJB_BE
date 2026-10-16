package com.jjinmak.back.shared.payment.dto;

/**
 * requestPayment / confirmPayment 의 결과.
 *
 * PAID         : 결제 완료. 주문은 PAID 로 바꾸면 된다
 * NEEDS_CHARGE : 예치금 부족. pgOrderId 와 chargeAmount 로 토스 결제창을 띄운 뒤 confirmPayment 를 호출해야 한다
 */
public record PaymentResultDto(
        Status status,
        Long paymentId,
        long totalPrice,
        long chargeAmount,
        String pgOrderId,
        String orderName
) {
    public enum Status { PAID, NEEDS_CHARGE }

    public static PaymentResultDto paid(Long paymentId, long totalPrice) {
        return new PaymentResultDto(Status.PAID, paymentId, totalPrice, 0L, null, null);
    }

    public static PaymentResultDto needsCharge(Long paymentId, long totalPrice, long chargeAmount, String pgOrderId, String orderName) {
        return new PaymentResultDto(Status.NEEDS_CHARGE, paymentId, totalPrice, chargeAmount, pgOrderId, orderName);
    }

    public boolean isPaid() {
        return status == Status.PAID;
    }
}
