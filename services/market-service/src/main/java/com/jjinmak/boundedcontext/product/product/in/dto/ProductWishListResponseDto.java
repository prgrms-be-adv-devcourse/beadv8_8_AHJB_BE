package com.jjinmak.boundedcontext.product.product.in.dto;

import java.util.List;

public record ProductWishListResponseDto(
        List<ProductWishDto> wishes,
        boolean hasNext
) {
}
