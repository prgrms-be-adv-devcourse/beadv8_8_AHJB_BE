package com.jjinmak.search.in.dto;

import com.jjinmak.contracts.product.domain.Manufacturer;
import com.jjinmak.contracts.product.domain.ProductCategory;
import com.jjinmak.search.domain.ProductSearchDocument;
import com.jjinmak.search.domain.ProductSearchStatus;

import java.time.LocalDateTime;

// 검색 결과 카드 한 장
public record ProductSearchItemDto(
        Long productId,
        String productName,
        ProductCategory category,
        Manufacturer manufacturer,
        String manufacturerName,
        String thumbnailUrl,                // 대표 이미지 (없으면 null)
        Long startPrice,
        Long instantWinPrice,               // 즉시 낙찰가 (설정 안 했으면 null)
        ProductSearchStatus status,
        Long auctionId,                     // 경매 시작 전이면 null
        LocalDateTime auctionScheduledAt,   // 경매 시작 예정 시각
        LocalDateTime auctionStartAt,       // 경매 시작 전이면 null
        LocalDateTime auctionEndAt,         // 경매 시작 전이면 null
        Long winningPrice,                  // 낙찰가 (SOLD일 때만 값이 있음)
        LocalDateTime registeredAt
) {
    public static ProductSearchItemDto of(ProductSearchDocument document) {
        return new ProductSearchItemDto(
                document.getProductId(),
                document.getProductName(),
                toEnum(ProductCategory.class, document.getCategory()),
                toEnum(Manufacturer.class, document.getManufacturer()),
                document.getManufacturerName(),
                document.getThumbnailUrl(),
                document.getStartPrice(),
                document.getInstantWinPrice(),
                document.getStatus(),
                document.getAuctionId(),
                document.getAuctionScheduledAt(),
                document.getAuctionStartAt(),
                document.getAuctionEndAt(),
                document.getWinningPrice(),
                document.getRegisteredAt()
        );
    }

    // 검색 문서엔 enum 이름(문자열)으로 저장돼 있다.
    private static <E extends Enum<E>> E toEnum(Class<E> type, String name) {
        return name == null ? null : Enum.valueOf(type, name);
    }
}
