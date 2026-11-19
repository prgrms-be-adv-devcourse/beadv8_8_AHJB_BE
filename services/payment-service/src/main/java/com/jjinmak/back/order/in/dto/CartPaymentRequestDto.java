package com.jjinmak.back.order.in.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record CartPaymentRequestDto(
        @NotEmpty
        List<@NotNull Long> productIds,
        String paymentMethod,
        @PositiveOrZero long pointUseAmount
) {
}
