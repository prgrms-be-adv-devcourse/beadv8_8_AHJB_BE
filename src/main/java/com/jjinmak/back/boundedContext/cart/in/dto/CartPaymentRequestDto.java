package com.jjinmak.back.boundedContext.cart.in.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CartPaymentRequestDto(
        @NotEmpty
        List<@NotNull Long> productIds
) {
}
