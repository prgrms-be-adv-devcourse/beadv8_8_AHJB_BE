package com.jjinmak.boundedcontext.market.product.in.dto;

import com.jjinmak.boundedcontext.market.product.domain.ProductAuctionFailureReason;
import com.jjinmak.boundedcontext.market.product.domain.ProductAuctionStatus;

import java.time.LocalDateTime;

// 상품 화면에 보여 줄 경매 상태. 경매 컨텍스트에서 읽어 상품의 상태 이름으로 바꾼 값이다.
public record ProductAuctionStateDto(
        ProductAuctionStatus status,
        LocalDateTime endAt,                        // 진행 중·종료된 경매의 종료 시각 (네덜란드식은 null)
        ProductAuctionFailureReason failureReason   // FAILED일 때만 값이 있음
) {
    // 아직 경매가 열리지 않은 상품
    public static ProductAuctionStateDto scheduled() {
        return new ProductAuctionStateDto(ProductAuctionStatus.SCHEDULED, null, null);
    }
}
