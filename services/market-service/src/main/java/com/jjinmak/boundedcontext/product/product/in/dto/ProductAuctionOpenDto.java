package com.jjinmak.boundedcontext.product.product.in.dto;

import com.jjinmak.boundedcontext.product.product.domain.AuctionType;
import com.jjinmak.boundedcontext.product.product.domain.Product;
import com.jjinmak.boundedcontext.product.product.domain.ProductAuctionTerms;

import java.time.LocalDateTime;

// 경매를 열 때 필요한 상품의 판매 조건. 시작·종료 시각은 상품에서 확정한다.
// 경매 방식에 맞는 조건만 채우고 다른 방식의 조건은 null이다.
public record ProductAuctionOpenDto(
        Long productId,
        int round,                      // 열 경매의 회차
        Long sellerId,
        AuctionType auctionType,
        Long startPrice,
        Long shippingFee,
        String category,                // ProductCategory.name()
        LocalDateTime startAt,          // 시작 예정 시각
        Long instantWinPrice,           // 영국식 (선택)
        LocalDateTime endAt,            // 영국식
        Long floorPrice,                // 네덜란드식
        Integer dropRatePercent,        // 네덜란드식
        Integer dropIntervalSec         // 네덜란드식
) {
    public static ProductAuctionOpenDto from(Product product) {
        LocalDateTime startAt = product.getAuctionScheduledAt();
        return of(product, product.getAuctionTerms().resolveEndAt(startAt));
    }

    // 종료 시각을 따로 정한 경우 (재등록: 이전 회차와 같은 기간)
    public static ProductAuctionOpenDto of(Product product, LocalDateTime endAt) {
        ProductAuctionTerms terms = product.getAuctionTerms();
        LocalDateTime startAt = product.getAuctionScheduledAt();
        boolean dutch = terms.isDutch();

        return new ProductAuctionOpenDto(
                product.getId(),
                product.getRound(),
                product.getSeller().getId(),
                terms.getAuctionType(),
                terms.getStartPrice(),
                product.getShippingFee(),
                product.getCategory().name(),
                startAt,
                dutch ? null : terms.getInstantWinPrice(),
                dutch ? null : endAt,
                dutch ? terms.getFloorPrice() : null,
                dutch ? terms.getDropRatePercent() : null,
                dutch ? terms.getDropIntervalSec() : null
        );
    }
}
