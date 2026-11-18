package com.jjinmak.back.boundedContext.review.in.dto;

import java.util.List;

public record ReviewListResponseDto(
        List<ReviewDto> reviews,
        boolean hasNext
) {
}
