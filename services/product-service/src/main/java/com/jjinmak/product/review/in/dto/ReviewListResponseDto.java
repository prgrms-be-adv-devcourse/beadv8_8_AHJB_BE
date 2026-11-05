package com.jjinmak.product.review.in.dto;

import java.util.List;

public record ReviewListResponseDto(
        List<ReviewDto> reviews,
        boolean hasNext
) {
}
