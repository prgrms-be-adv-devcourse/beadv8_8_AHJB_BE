package com.jjinmak.back.boundedContext.product.in.dto;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductAuctionFailureReason;
import com.jjinmak.back.boundedContext.product.domain.ProductAuctionStatus;
import com.jjinmak.back.boundedContext.product.domain.ProductAuctionTerms;
import com.jjinmak.back.boundedContext.product.domain.ProductCategory;

import java.time.LocalDateTime;

// '내 상품' 목록 한 줄. 상품 정보 + 경매 상태·결과만 담는다. (현재가·입찰 수·낙찰가 없음)
public record ProductMyItemDto(
        Long productId,
        Long auctionId,             // 경매 시작 전이면 null
        String productName,
        String thumbnailUrl,        // 대표 이미지 (없으면 null)
        ProductCategory category,
        Long startPrice,
        Long instantWinPrice,
        ProductAuctionStatus status,
        LocalDateTime auctionScheduledAt,   // 경매 시작 예정 시각
        LocalDateTime auctionEndAt,         // 진행 중일 때 종료 예정 시각 (연장 미반영)
        ProductAuctionFailureReason failureReason,  // FAILED일 때만 값이 있음
        boolean editable,           // 수정/삭제 가능 여부 (시작 예정 시각 전)
        LocalDateTime createdAt
) {
    public static ProductMyItemDto of(Product product, String thumbnailUrl, LocalDateTime now) {
        ProductAuctionTerms terms = product.getAuctionTerms();

        return new ProductMyItemDto(
                product.getId(),
                product.getCurrentAuctionId(),
                product.getName(),
                thumbnailUrl,
                product.getCategory(),
                terms.getStartPrice(),
                terms.getInstantWinPrice(),
                product.getAuctionStatus(),
                product.getAuctionScheduledAt(),
                product.getAuctionEndAt(),
                product.getAuctionFailureReason(),
                product.isEditable(now),
                product.getCreatedAt()
        );
    }
}
