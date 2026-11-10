package com.jjinmak.contracts.product.event;

import java.time.LocalDateTime;

// 확장 단계: 상품 전환 후 @Deprecated 필드와 옛 생성자 삭제
public record ProductRegisteredEvent(
        Long productId,
        Long sellerId,
        Long shippingFee,
        Long startPrice,
        @Deprecated Long instantWinPrice,
        @Deprecated int duration,
        @Deprecated LocalDateTime customEndAt,
        String productName,                     // 정산이 상품명 기록용으로 사용
        String category,
        @Deprecated String thumbnailUrl,
        String auctionType,         // "ENGLISH" | "DUTCH", null이면 옛 형태(ENGLISH)
        LocalDateTime scheduledAt,
        EnglishTerms english,
        DutchTerms dutch
) {

    @Deprecated
    public ProductRegisteredEvent(Long productId, Long sellerId, Long shippingFee, Long startPrice,
                                  Long instantWinPrice, int duration, LocalDateTime customEndAt,
                                  String productName, String category, String thumbnailUrl) {
        this(productId, sellerId, shippingFee, startPrice, instantWinPrice, duration, customEndAt,
                productName, category, thumbnailUrl, null, null, null, null);
    }

    public record EnglishTerms(Long instantWinPrice, LocalDateTime endAt) {}

    public record DutchTerms(Long floorPrice, Integer dropRatePercent, Integer dropIntervalSec) {}
}