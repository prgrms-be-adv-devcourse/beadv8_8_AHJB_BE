package com.jjinmak.contracts.event;

public record OrderRefundAcceptedEvent(
        Long orderId,
        Long auctionId
) {
}
