package com.jjinmak.back.boundedContext.payment.in.dto;

import jakarta.validation.constraints.Positive;

/** POST /api/v1/payments/charges 요청 본문 */
public record ChargeRequestDto(
        @Positive(message = "충전 금액은 0보다 커야 합니다.")
        long amount
) {
}
