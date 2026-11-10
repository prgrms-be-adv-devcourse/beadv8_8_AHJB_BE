package com.jjinmak.contracts.auction.event;

import java.time.LocalDateTime;

public record AuctionFailedEvent (
        Long auctionId,
        Long productId,
        String failureReason,
        LocalDateTime failedAt,
        Long sellerId
){
    @Deprecated
    public AuctionFailedEvent(Long auctionId, Long productId, String failureReason, LocalDateTime failedAt) {
        this(auctionId, productId, failureReason, failedAt, null);
    }
}
