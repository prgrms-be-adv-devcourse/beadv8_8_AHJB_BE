package com.jjinmak.back.boundedContext.product.in.dto;

public record ProductWishResponseDto(
        Long productId,
        boolean wished,     // 요청 처리 후 찜 상태
        int wishCount       // 요청 처리 후 상품의 찜 수
) {
}
