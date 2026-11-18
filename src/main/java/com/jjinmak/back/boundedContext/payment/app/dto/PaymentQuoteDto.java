package com.jjinmak.back.boundedContext.payment.app.dto;

/**
 * 결제 전 견적. "지금 잔액으로 이 금액을 결제하면 예치금에서 얼마, 카드로 얼마가 나가는가".
 * 아무것도 저장하지 않는다. 실제 결제 시점의 잔액이 다르면 결과도 달라질 수 있다.
 */
public record PaymentQuoteDto(
        long balance,
        long amount,
        long walletUseAmount,
        long shortage,
        boolean needsCharge
) {
    public static PaymentQuoteDto of(long balance, long amount) {
        long shortage = Math.max(0L, amount - balance);
        return new PaymentQuoteDto(balance, amount, amount - shortage, shortage, shortage > 0);
    }
}
