package com.jjinmak.back.boundedContext.auction.dto;

import java.time.LocalDateTime;

public record AuctionBidResponseDto (
        Long bidId,
        Long bidPrice,
        Long currentPrice,
        Long minBidPrice,
        LocalDateTime endAt,
        boolean isExtended,
        boolean isInstantWin
) {
}
