package com.jjinmak.back.shared.auction.event;

import java.time.LocalDateTime;

public record AuctionBidUpdatedEvent (
        Long auctionId,
        Long productId,
        Long currentPrice,
        int bidCount,
        LocalDateTime endAt
){
}
