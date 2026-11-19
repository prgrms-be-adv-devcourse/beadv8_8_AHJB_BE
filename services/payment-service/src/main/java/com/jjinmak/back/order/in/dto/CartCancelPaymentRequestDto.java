package com.jjinmak.back.order.in.dto;

import jakarta.validation.constraints.NotNull;

public record CartCancelPaymentRequestDto(
        @NotNull
        Long orderGroupId
) {
}
