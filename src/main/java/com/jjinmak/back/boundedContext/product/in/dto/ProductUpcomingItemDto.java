package com.jjinmak.back.boundedContext.product.in.dto;

import com.jjinmak.back.boundedContext.product.domain.Manufacturer;
import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductAuctionTerms;
import com.jjinmak.back.boundedContext.product.domain.ProductCategory;

import java.time.LocalDateTime;

// 곧 시작 목록 카드 한 장. 경매 목록 카드와 같은 화면에서 쓰이므로 경매 관련 필드도 시작 전 값으로 채운다.
public record ProductUpcomingItemDto(
        Long productId,
        Long auctionId,         // 경매 시작 전이라 항상 null
        String productName,
        String thumbnailUrl,    // 대표 이미지 (없으면 null)
        ProductCategory category,
        Manufacturer manufacturer,
        String status,          // 항상 SCHEDULED
        Long startPrice,
        Long currentPrice,      // 시작 전이라 시작가와 같다
        Long instantWinPrice,
        int bidCount,           // 시작 전이라 0
        int wishCount,
        Long shippingFee,
        LocalDateTime startAt,  // 경매 시작 예정 시각
        LocalDateTime endAt     // 시작 전이라 null
) {
    private static final String SCHEDULED = "SCHEDULED";

    public static ProductUpcomingItemDto of(Product product, String thumbnailUrl) {
        ProductAuctionTerms terms = product.getAuctionTerms();

        return new ProductUpcomingItemDto(
                product.getId(),
                null,
                product.getName(),
                thumbnailUrl,
                product.getCategory(),
                product.getManufacturer(),
                SCHEDULED,
                terms.getStartPrice(),
                terms.getStartPrice(),
                terms.getInstantWinPrice(),
                0,
                product.getWishCount(),
                product.getShippingFee(),
                product.getAuctionScheduledAt(),
                null
        );
    }
}
