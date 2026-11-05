package com.jjinmak.product.product.in.dto;

public record ProductImageUploadResponseDto(
        Long imageId,       // 상품 등록/수정 시 imageIds로 보낸다.
        String imageUrl     // 미리보기용
) {
}
