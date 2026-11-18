package com.jjinmak.back.shared.payment.dto;

/**
 * requestPayment 의 결과. 주문은 status 만 보고, 나머지는 해석 없이 클라이언트에 그대로 내려준다.
 *
 * PAID         : 결제 완료. 결제 완료 이벤트(PaymentPaidEvent)도 함께 발행된다
 * NEEDS_CHARGE : 예치금 부족. 클라이언트가 pgOrderId / chargeAmount / orderName 으로 토스 결제창을 띄운 뒤
 *                successUrl 로 돌아와 결제 API(POST /api/v1/payments/confirm)를 호출한다
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
