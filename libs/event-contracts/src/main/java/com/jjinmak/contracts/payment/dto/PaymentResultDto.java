package com.jjinmak.contracts.payment.dto;

/**
 * requestPayment 의 결과. 주문은 status 만 보고, 나머지는 해석 없이 클라이언트에 그대로 내려준다.
 *
 * PAID          : 결제 완료. 결제 완료 이벤트(PaymentPaidEvent)도 함께 발행된다
 * NEEDS_CHARGE  : 예치금 결제인데 예치금이 부족하다. 결제창에서 부족분을 충전해야 한다
 * NEEDS_PAYMENT : 직접 결제(계좌이체 · 카드)다. 결제창에서 주문 대금을 결제해야 한다

 * - chargeAmount   : 결제창에서 결제할 금액. 충전이면 최소 충전 금액 때문에 부족분보다 클 수 있다
 * - shortageAmount : 충전일 때 실제로 모자란 금액. chargeAmount − shortageAmount 는 결제 후 예치금에 남는다.
 *                    직접 결제면 chargeAmount 와 같다 (남는 돈이 없다)
 * - pgMethod       : 결제창을 열 때 써야 하는 결제수단 (TRANSFER / CARD). 예치금 충전은 TRANSFER 이고,
 *                    다른 수단으로 결제하면 승인 단계에서 취소된다
 * - payMethod      : 이 결제에 적용된 결제수단 (DEPOSIT / TRANSFER / CARD)
 */
public record PaymentResultDto(
        Status status,
        Long paymentId,
        long totalPrice,
        long chargeAmount,
        long shortageAmount,
        String pgOrderId,
        String orderName,
        String pgMethod,
        String payMethod,
        long pointAmount,
        long depositAmount,
        long pgAmount
) {
    public enum Status { PAID, NEEDS_CHARGE, NEEDS_PAYMENT }

    // 금액 구성 (이 결제를 무엇으로 얼마씩 내는지)
    public record Breakdown(String payMethod, long pointAmount, long depositAmount, long pgAmount) {
        // 전액 예치금 결제
        public static Breakdown depositOnly(long totalPrice) {
            return new Breakdown("DEPOSIT", 0L, totalPrice, 0L);
        }
    }

    // 전액 예치금 결제 완료. 기존 호출부와의 호환을 위해 남겨 둔다
    public static PaymentResultDto paid(Long paymentId, long totalPrice) {
        return paid(paymentId, totalPrice, Breakdown.depositOnly(totalPrice));
    }

    public static PaymentResultDto paid(Long paymentId, long totalPrice, Breakdown breakdown) {
        return new PaymentResultDto(Status.PAID, paymentId, totalPrice, 0L, 0L, null, null, null,
                breakdown.payMethod(), breakdown.pointAmount(), breakdown.depositAmount(), breakdown.pgAmount());
    }

    // 부족분과 충전 금액이 같고 결제수단을 따로 알리지 않는 경우. 기존 호출부와의 호환을 위해 남겨 둔다
    public static PaymentResultDto needsCharge(Long paymentId, long totalPrice, long chargeAmount, String pgOrderId, String orderName) {
        return needsCharge(paymentId, totalPrice, chargeAmount, chargeAmount, pgOrderId, orderName, null);
    }

    // 전액 예치금 결제의 부족분 충전. 기존 호출부와의 호환을 위해 남겨 둔다
    public static PaymentResultDto needsCharge(Long paymentId, long totalPrice, long chargeAmount, long shortageAmount,
                                               String pgOrderId, String orderName, String pgMethod) {
        return needsCharge(paymentId, totalPrice, chargeAmount, shortageAmount, pgOrderId, orderName, pgMethod,
                Breakdown.depositOnly(totalPrice));
    }

    public static PaymentResultDto needsCharge(Long paymentId, long totalPrice, long chargeAmount, long shortageAmount,
                                               String pgOrderId, String orderName, String pgMethod, Breakdown breakdown) {
        return new PaymentResultDto(Status.NEEDS_CHARGE, paymentId, totalPrice, chargeAmount, shortageAmount,
                pgOrderId, orderName, pgMethod,
                breakdown.payMethod(), breakdown.pointAmount(), breakdown.depositAmount(), breakdown.pgAmount());
    }

    // 직접 결제. 결제창 금액은 breakdown.pgAmount 와 같다
    public static PaymentResultDto needsPayment(Long paymentId, long totalPrice, String pgOrderId, String orderName,
                                                String pgMethod, Breakdown breakdown) {
        return new PaymentResultDto(Status.NEEDS_PAYMENT, paymentId, totalPrice, breakdown.pgAmount(), breakdown.pgAmount(),
                pgOrderId, orderName, pgMethod,
                breakdown.payMethod(), breakdown.pointAmount(), breakdown.depositAmount(), breakdown.pgAmount());
    }

    public boolean isPaid() {
        return status == Status.PAID;
    }

    // 결제창을 거쳐야 하는지 (충전이든 직접 결제든)
    public boolean needsPgWindow() {
        return status == Status.NEEDS_CHARGE || status == Status.NEEDS_PAYMENT;
    }
}
