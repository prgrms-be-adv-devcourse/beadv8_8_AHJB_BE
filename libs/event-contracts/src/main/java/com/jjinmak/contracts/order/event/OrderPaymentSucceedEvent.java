package com.jjinmak.contracts.order.event;

import java.time.LocalDateTime;

public record OrderPaymentSucceedEvent(
        Long orderId,
        Long auctionId,
        Long productId,
        Long winnerId,
        Long sellerId,
        LocalDateTime paidAt
) {
}
