package com.jjinmak.back.boundedContext.review.in.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// 값이 있는 항목만 수정한다. (null이면 기존 값 유지)
public record ReviewUpdateRequestDto(
        BigDecimal rating,

        @Size(max = 1000)
        @Pattern(regexp = "(?s).*\\S.*")
        String content
) {
}
