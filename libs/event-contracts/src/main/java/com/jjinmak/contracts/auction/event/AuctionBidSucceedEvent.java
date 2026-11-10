package com.jjinmak.contracts.auction.event;

import java.time.LocalDateTime;

public record AuctionBidSucceedEvent(
        Long bidId,
        Long auctionId,
        Long productId,
        Long bidderId,
        Long bidPrice,
        int bidCount,
        LocalDateTime endAt,
        LocalDateTime bidAt
) {
}
