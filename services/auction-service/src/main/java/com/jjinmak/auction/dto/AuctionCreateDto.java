package com.jjinmak.auction.dto;

import java.time.LocalDateTime;

public record AuctionCreateDto(
        Long productId,
        Long sellerId,
        Long startPrice,
        Long instantWinPrice,
        Long shippingFee,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String category
) {
}
