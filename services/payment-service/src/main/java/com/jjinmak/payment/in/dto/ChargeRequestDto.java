package com.jjinmak.payment.in.dto;

import jakarta.validation.constraints.Positive;

public record ChargeRequestDto(
        @Positive(message = "충전 금액은 0보다 커야 합니다.")
        long amount
) {
}
