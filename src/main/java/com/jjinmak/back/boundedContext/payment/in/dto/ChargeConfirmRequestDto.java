package com.jjinmak.back.boundedContext.payment.in.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * POST /api/v1/payments/charges/confirm 요청 본문.
 * 토스 successUrl 로 돌아온 쿼리 파라미터(paymentKey, orderId, amount)를 그대로 넣는다. orderId 가 pgOrderId 다.
 */
public record ChargeConfirmRequestDto(
        @NotBlank String pgOrderId,
        @NotBlank String paymentKey,
        @Positive long amount
) {
}
