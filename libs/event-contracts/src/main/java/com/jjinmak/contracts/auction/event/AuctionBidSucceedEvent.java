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
        LocalDateTime bidAt,
        Long previousHighestBidderId   // 이전 최고입찰자.
) {
    @Deprecated
    public AuctionBidSucceedEvent(Long bidId, Long auctionId, Long productId, Long bidderId,
                                  Long bidPrice, int bidCount, LocalDateTime endAt, LocalDateTime bidAt) {
        this(bidId, auctionId, productId, bidderId, bidPrice, bidCount, endAt, bidAt, null);
    }
}