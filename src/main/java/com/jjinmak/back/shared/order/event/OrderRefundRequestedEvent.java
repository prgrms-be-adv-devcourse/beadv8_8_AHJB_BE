package com.jjinmak.back.shared.order.event;

public record OrderRefundRequestedEvent(
        Long orderId,
        Long winnerId,
        Long sellerId,
        Long productId,
        Long winningPrice
) {
}
