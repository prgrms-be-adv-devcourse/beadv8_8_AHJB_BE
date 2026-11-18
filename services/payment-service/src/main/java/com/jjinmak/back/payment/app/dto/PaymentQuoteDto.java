package com.jjinmak.back.payment.app.dto;

/**
 * 결제 전 견적. "지금 잔액으로 이 금액을 결제하면 예치금에서 얼마가 나가고, 모자라면 얼마를 충전해야 하는가".
 *
 * - walletUseAmount     : 지금 예치금으로 충당되는 금액 = 결제 금액 − 부족분
 * - shortage            : 부족분. 0 이면 충전 없이 결제된다
 * - chargeAmount        : 실제로 충전해야 하는 금액. 부족분이 최소 충전 금액보다 작으면 최소 충전 금액으로 올린다
 * - balanceAfterPayment : 충전하고 결제한 뒤 예치금에 남는 금액 = 잔액 + 충전 금액 − 결제 금액
 * - pgMethod            : 충전할 때 결제창을 열어야 하는 결제수단 (TRANSFER). 충전이 필요 없으면 null
 * - chargeLimitExceeded : 충전 금액이 1회 충전 한도를 넘는지. true 면 이 금액으로는 결제 요청이 거절된다 (예치금을 먼저 충전해야 한다)
 */
public record PaymentQuoteDto(
        long balance,
        long amount,
        long walletUseAmount,
        long shortage,
        boolean needsCharge,
        long chargeAmount,
        long balanceAfterPayment,
        String pgMethod,
        boolean chargeLimitExceeded
) {
    public static PaymentQuoteDto of(long balance, long amount, long chargeAmount, String pgMethod, boolean chargeLimitExceeded) {
        long shortage = Math.max(0L, amount - balance);
        return new PaymentQuoteDto(balance, amount, amount - shortage, shortage, shortage > 0,
                chargeAmount, balance + chargeAmount - amount, shortage > 0 ? pgMethod : null, chargeLimitExceeded);
    }
}
