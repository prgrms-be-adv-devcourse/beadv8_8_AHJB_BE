package com.jjinmak.back.boundedContext.product.in.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductQnaUpdateRequestDto(
        @NotBlank
        @Size(max = 1000)
        String content
) {
}
