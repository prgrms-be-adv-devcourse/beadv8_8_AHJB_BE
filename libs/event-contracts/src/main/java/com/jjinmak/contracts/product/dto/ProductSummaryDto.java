package com.jjinmak.contracts.product.dto;

public record ProductSummaryDto(
        Long productId,
        String name,
        String thumbnailUrl     // 대표 이미지 (없으면 null)
) {
}
