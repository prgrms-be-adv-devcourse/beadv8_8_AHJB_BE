package com.jjinmak.back.boundedContext.product.in.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductQnaCreateRequestDto(
        // null이면 질문, 값이 있으면 해당 질문에 다는 답글 (판매자만 가능)
        Long parentId,

        @NotBlank
        @Size(max = 1000)
        String content
) {
}
