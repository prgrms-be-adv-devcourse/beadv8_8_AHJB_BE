package com.jjinmak.back.boundedContext.auction.dto;

import com.jjinmak.back.boundedContext.auction.domain.Auction;
import com.jjinmak.back.boundedContext.auction.domain.ProductSnapshot;

import java.time.LocalDateTime;

public record AuctionMyBidDto(
        Long auctionId,
        Long productId,
        String productName,
        String thumbnailUrl,
        Long currentPrice,
        boolean isHighestBidder,   // false면 "밀림" → 다시 입찰 버튼 노출
        LocalDateTime endAt
) {
    public static AuctionMyBidDto of(Auction auction, Long memberId) {
        ProductSnapshot snapshot = auction.getProductSnapshot();
        return new AuctionMyBidDto(
                auction.getId(),
                auction.getProductId(),
                snapshot != null ? snapshot.getProductName() : null,
                snapshot != null ? snapshot.getThumbnailUrl() : null,
                auction.getCurrentPrice(),
                auction.isHighestBidder(memberId),
                auction.getEndAt()
        );
    }
}