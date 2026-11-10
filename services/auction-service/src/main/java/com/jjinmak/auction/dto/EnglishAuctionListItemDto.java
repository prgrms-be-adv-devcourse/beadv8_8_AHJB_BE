package com.jjinmak.auction.dto;

import com.jjinmak.auction.domain.AuctionStatus;
import com.jjinmak.auction.domain.EnglishAuction;

import java.time.LocalDateTime;

public record EnglishAuctionListItemDto(
        Long auctionId,
        Long productId,
        String category,
        AuctionStatus status,
        Long currentPrice,
        Long instantWinPrice,
        int bidCount,
        LocalDateTime endAt
) {
    public static EnglishAuctionListItemDto from(EnglishAuction auction, LocalDateTime now) {
        return new EnglishAuctionListItemDto(
                auction.getId(),
                auction.getProductId(),
                auction.getCategory(),
                auction.getStatus(),
                auction.getWinningPrice() != null ? auction.getWinningPrice() : auction.currentPriceAt(now),
                auction.getInstantWinPrice(),
                auction.getBidCount(),
                auction.getEndAt()
        );
    }
}
