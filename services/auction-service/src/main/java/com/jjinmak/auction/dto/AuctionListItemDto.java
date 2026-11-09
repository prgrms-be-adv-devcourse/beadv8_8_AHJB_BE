package com.jjinmak.auction.dto;

import com.jjinmak.auction.domain.Auction;
import com.jjinmak.auction.domain.AuctionStatus;
import com.jjinmak.auction.domain.ProductSnapshot;

import java.time.LocalDateTime;

// 경매 목록 카드 한 장
public record AuctionListItemDto(
        Long auctionId,
        Long productId,
        String productName,
        String category,
        String thumbnailUrl,
        AuctionStatus status,
        Long currentPrice,      // 종료된 경매는 낙찰가
        Long instantWinPrice,   // 미설정이면 null
        int bidCount,
        LocalDateTime endAt
) {
    public static AuctionListItemDto from(Auction auction, LocalDateTime now) {
        ProductSnapshot snapshot = auction.getProductSnapshot();
        return new AuctionListItemDto(
                auction.getId(),
                auction.getProductId(),
                snapshot != null ? snapshot.getProductName() : null,
                snapshot != null ? snapshot.getCategory() : null,
                snapshot != null ? snapshot.getThumbnailUrl() : null,
                auction.getStatus(),
                auction.getWinningPrice() != null ? auction.getWinningPrice() : auction.currentPriceAt(now),
                auction.getInstantWinPrice(),
                auction.getBidCount(),
                auction.getEndAt()
        );
    }
}