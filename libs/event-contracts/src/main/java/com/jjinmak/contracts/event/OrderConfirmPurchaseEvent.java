package com.jjinmak.contracts.event;

import java.time.LocalDateTime;

public record OrderConfirmPurchaseEvent(
        Long orderId,
        Long winnerId,
        Long sellerId,
        Long auctionId,
        Long productId,
        Long winningPrice,
        Long deliveryFee,
        LocalDateTime confirmedAt
) {
}
