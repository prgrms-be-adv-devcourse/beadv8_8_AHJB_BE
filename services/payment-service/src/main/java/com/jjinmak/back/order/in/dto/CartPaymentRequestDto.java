package com.jjinmak.back.order.in.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CartPaymentRequestDto(
        @NotEmpty
        List<@NotNull Long> productIds
) {
}
