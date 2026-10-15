package com.jjinmak.back.shared.order.event;

public record OrderRefundAcceptedEvent(
        Long orderId,
        Long auctionId
) {
}
