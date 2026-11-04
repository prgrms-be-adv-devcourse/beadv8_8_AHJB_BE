package com.jjinmak.contracts.action.event;

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
