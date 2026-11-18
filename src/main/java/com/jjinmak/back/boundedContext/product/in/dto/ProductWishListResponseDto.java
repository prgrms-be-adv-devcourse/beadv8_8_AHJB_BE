package com.jjinmak.back.boundedContext.product.in.dto;

import java.util.List;

public record ProductWishListResponseDto(
        List<ProductWishDto> wishes,
        boolean hasNext
) {
}
