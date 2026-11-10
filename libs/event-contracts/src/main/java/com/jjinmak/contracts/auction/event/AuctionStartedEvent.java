package com.jjinmak.contracts.auction.event;

import java.time.LocalDateTime;

public record AuctionStartedEvent (
        Long auctionId,
        Long productId,
        Long startPrice,
        Long instantWinPrice,   // 영국식만
        LocalDateTime startAt,
        LocalDateTime endAt,    // 네덜란드식은 null (최저가를 역산할 수 있어 보내지 않음)
        String auctionType      // "ENGLISH" | "DUTCH"
){
}
