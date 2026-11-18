package com.jjinmak.contracts.payment.dto;

/**
 * requestPayment 의 결과. 주문은 status 만 보고, 나머지는 해석 없이 클라이언트에 그대로 내려준다.
 *
 * PAID         : 결제 완료. 결제 완료 이벤트(PaymentPaidEvent)도 함께 발행된다
 * NEEDS_CHARGE : 예치금 부족. 클라이언트가 pgOrderId / chargeAmount / orderName / pgMethod 로 토스 결제창을 띄운 뒤
 *                successUrl 로 돌아와 결제 API(POST /api/v1/payments/confirm)를 호출한다
 *
 * - chargeAmount   : 결제창에서 결제(충전)할 금액. 최소 충전 금액 때문에 부족분보다 클 수 있다
 * - shortageAmount : 실제로 모자란 금액. chargeAmount − shortageAmount 는 결제 후 예치금에 남는다
 * - pgMethod       : 결제창을 열 때 써야 하는 결제수단 (예치금 충전은 TRANSFER). 다른 수단으로 결제하면 승인 단계에서 취소된다
 */
public record PaymentResultDto(
        Status status,
        Long paymentId,
        long totalPrice,
        long chargeAmount,
        long shortageAmount,
        String pgOrderId,
        String orderName,
        String pgMethod
) {
    public enum Status { PAID, NEEDS_CHARGE }

    public static PaymentResultDto paid(Long paymentId, long totalPrice) {
        return new PaymentResultDto(Status.PAID, paymentId, totalPrice, 0L, 0L, null, null, null);
    }

    // 부족분과 충전 금액이 같고 결제수단을 따로 알리지 않는 경우. 기존 호출부와의 호환을 위해 남겨 둔다
    public static PaymentResultDto needsCharge(Long paymentId, long totalPrice, long chargeAmount, String pgOrderId, String orderName) {
        return needsCharge(paymentId, totalPrice, chargeAmount, chargeAmount, pgOrderId, orderName, null);
    }

    public static PaymentResultDto needsCharge(Long paymentId, long totalPrice, long chargeAmount, long shortageAmount,
                                               String pgOrderId, String orderName, String pgMethod) {
        return new PaymentResultDto(Status.NEEDS_CHARGE, paymentId, totalPrice, chargeAmount, shortageAmount,
                pgOrderId, orderName, pgMethod);
    }

    public boolean isPaid() {
        return status == Status.PAID;
    }
}
