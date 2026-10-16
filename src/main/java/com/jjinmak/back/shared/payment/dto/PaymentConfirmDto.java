package com.jjinmak.back.shared.payment.dto;

/**
 * 토스 결제창 성공 복귀 후 주문 → 결제로 넘기는 승인 요청.
 * paymentKey / pgOrderId / amount 는 토스 successUrl 쿼리 파라미터(paymentKey, orderId, amount)를 그대로 전달한다.
 */
public record PaymentConfirmDto(
        Long orderGroupId,
        Long buyerId,
        String pgOrderId,
        String paymentKey,
        long amount
) {
}
