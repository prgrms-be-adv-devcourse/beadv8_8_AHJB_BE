package com.jjinmak.back.boundedContext.payment.in.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * POST /api/v1/payments/confirm 요청 본문 (주문 결제 승인).
 * 토스 successUrl 로 돌아온 쿼리 파라미터(paymentKey, orderId, amount)를 그대로 넣는다. orderId 가 pgOrderId 다.
 * 주문 그룹 id 는 받지 않는다. pgOrderId 로 결제를 찾는다.
 */
public record PaymentConfirmRequestDto(
        @NotBlank String pgOrderId,
        @NotBlank String paymentKey,
        @Positive long amount
) {
}
