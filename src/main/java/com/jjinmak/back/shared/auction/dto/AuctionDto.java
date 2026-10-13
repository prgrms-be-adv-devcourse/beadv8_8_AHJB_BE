package com.jjinmak.back.shared.auction.dto;

import java.time.LocalDateTime;

public record AuctionDto(
        Long auctionId,
        Long winnerId,
        Long sellerId,
        Long productId,
        Long winningPrice,
        Long shippingFee,
        LocalDateTime winAt
) {
}
