package com.jjinmak.boundedcontext.auction.dto;

import com.jjinmak.boundedcontext.auction.domain.AuctionStatus;
import com.jjinmak.boundedcontext.auction.domain.EnglishAuction;
import com.jjinmak.contracts.product.dto.ProductSummaryDto;

import java.time.LocalDateTime;

public record EnglishAuctionListItemDto(
        Long auctionId,
        Long productId,
        String productName,     // 상품을 찾지 못하면 null
        String thumbnailUrl,    // 대표 이미지가 없으면 null
        String category,
        AuctionStatus status,
        Long currentPrice,
        Long instantWinPrice,
        int bidCount,
        LocalDateTime endAt
) {
    public static EnglishAuctionListItemDto from(EnglishAuction auction, ProductSummaryDto product, LocalDateTime now) {
        return new EnglishAuctionListItemDto(
                auction.getId(),
                auction.getProductId(),
                product != null ? product.name() : null,
                product != null ? product.thumbnailUrl() : null,
                auction.getCategory(),
                auction.getStatus(),
                auction.getWinningPrice() != null ? auction.getWinningPrice() : auction.currentPriceAt(now),
                auction.getInstantWinPrice(),
                auction.getBidCount(),
                auction.getEndAt()
        );
    }
}
