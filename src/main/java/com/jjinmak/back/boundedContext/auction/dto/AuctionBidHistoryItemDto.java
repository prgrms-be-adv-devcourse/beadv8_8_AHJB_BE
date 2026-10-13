package com.jjinmak.back.boundedContext.auction.dto;

import java.time.LocalDateTime;

public record AuctionBidHistoryItemDto (
        Long bidId,
        String bidderNickname,
        Long bidPrice,
        LocalDateTime bidAt,
        boolean isMine
){
}
