package com.jjinmak.boundedcontext.auction.dto;

import com.jjinmak.boundedcontext.auction.domain.EnglishAuction;
import com.jjinmak.contracts.product.dto.ProductSummaryDto;

import java.time.LocalDateTime;

public record AuctionMyBidDto(
        Long auctionId,
        Long productId,
        String productName,     // 상품을 찾지 못하면 null
        String thumbnailUrl,    // 대표 이미지가 없으면 null
        Long currentPrice,
        boolean isHighestBidder,
        LocalDateTime endAt
) {
    public static AuctionMyBidDto of(EnglishAuction auction, ProductSummaryDto product, Long memberId, LocalDateTime now) {
        return new AuctionMyBidDto(
                auction.getId(),
                auction.getProductId(),
                product != null ? product.name() : null,
                product != null ? product.thumbnailUrl() : null,
                auction.currentPriceAt(now),
                auction.isHighestBidder(memberId),
                auction.getEndAt()
        );
    }
}
