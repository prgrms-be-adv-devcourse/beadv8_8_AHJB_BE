package com.jjinmak.back.shared.auction.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionDto(
        UUID winnerId,
        UUID sellerId,
        Long productId,
        Long winningPrice,
        Long deliveryFee,
        LocalDateTime winAt
) {
}
