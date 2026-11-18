package com.jjinmak.contracts.review.dto;

public record SellerRatingDto(
        Double ratingAvg,   // 받은 후기가 없으면 null
        long reviewCount
) {
}
