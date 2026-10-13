package com.jjinmak.back.shared.auction.event;

import java.time.LocalDateTime;

public record AuctionFailedEvent (
        Long auctionId,
        Long productId,
        String failureReason,
        LocalDateTime failedAt
){
}
