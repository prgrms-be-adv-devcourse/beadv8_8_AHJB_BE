package com.jjinmak.contracts.auction.event;

import java.time.LocalDateTime;

public record AuctionStartedEvent (
        Long auctionId,
        Long productId,
        Long startPrice,
        Long instantWinPrice,
        LocalDateTime startAt,
        LocalDateTime endAt
){
}
