package com.jjinmak.back.boundedContext.review.in.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ReviewCreateRequestDto(
        @NotNull
        Long orderId,

        // 0.5 ~ 5.0, 0.5 단위 (도메인에서 검증)
        @NotNull
        BigDecimal rating,

        @NotBlank
        @Size(max = 1000)
        String content
) {
}
