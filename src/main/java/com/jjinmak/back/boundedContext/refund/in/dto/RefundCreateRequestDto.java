package com.jjinmak.back.boundedContext.refund.in.dto;

import com.jjinmak.back.boundedContext.refund.domain.RefundReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RefundCreateRequestDto(
        @NotNull
        @Positive
        Long orderId,

        @NotNull
        RefundReason reason,

        @Size(max = 500)
        String detail
) {
}
