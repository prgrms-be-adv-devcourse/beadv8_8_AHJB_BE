package com.jjinmak.contracts.order.event;

public record OrderRefundAcceptedEvent(
        Long orderId,
        Long auctionId
) {
}
