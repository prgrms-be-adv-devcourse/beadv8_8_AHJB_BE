package com.jjinmak.back.shared.review.dto;

public record SellerRatingDto(
        Double ratingAvg,   // 받은 후기가 없으면 null
        long reviewCount
) {
}
