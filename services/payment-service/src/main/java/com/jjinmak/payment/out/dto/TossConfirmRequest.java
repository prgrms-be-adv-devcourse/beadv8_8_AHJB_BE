package com.jjinmak.payment.out.dto;

/** POST /v1/payments/confirm 요청 본문 */
public record TossConfirmRequest(
        String paymentKey,
        String orderId,
        long amount
) {
}
