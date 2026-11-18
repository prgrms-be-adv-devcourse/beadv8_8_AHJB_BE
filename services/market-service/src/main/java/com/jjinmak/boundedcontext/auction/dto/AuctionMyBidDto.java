package com.jjinmak.boundedcontext.auction.dto;

import com.jjinmak.boundedcontext.auction.domain.EnglishAuction;

import java.time.LocalDateTime;

public record AuctionMyBidDto(
        Long auctionId,
        Long productId,
        Long currentPrice,
        boolean isHighestBidder,
        LocalDateTime endAt
) {
    public static AuctionMyBidDto of(EnglishAuction auction, Long memberId, LocalDateTime now) {
        return new AuctionMyBidDto(
                auction.getId(),
                auction.getProductId(),
                auction.currentPriceAt(now),
                auction.isHighestBidder(memberId),
                auction.getEndAt()
        );
    }
}
