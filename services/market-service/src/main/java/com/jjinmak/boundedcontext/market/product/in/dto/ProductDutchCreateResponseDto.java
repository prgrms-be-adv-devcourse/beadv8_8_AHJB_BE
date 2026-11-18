package com.jjinmak.boundedcontext.market.product.in.dto;

import com.jjinmak.boundedcontext.market.product.domain.Product;
import com.jjinmak.boundedcontext.market.product.domain.ProductAuctionTerms;

import java.time.LocalDateTime;

// 관리자 네덜란드식 등록 응답. 관리자가 진행 모습을 미리 확인할 수 있게 예상 값을 함께 준다.
public record ProductDutchCreateResponseDto(
        Long productId,
        LocalDateTime openAt,
        Long dropAmount,          // 한 번에 내려가는 금액
        Long dropCount,           // 최저가까지 내려가는 횟수
        Long expectedDurationSec, // 아무도 사지 않았을 때 오픈부터 유찰까지 걸리는 시간(초)
        LocalDateTime expectedEndAt
) {
    public static ProductDutchCreateResponseDto from(Product product) {
        ProductAuctionTerms terms = product.getAuctionTerms();
        LocalDateTime openAt = product.getAuctionScheduledAt();

        return new ProductDutchCreateResponseDto(
                product.getId(),
                openAt,
                terms.dutchDropAmount(),
                terms.dutchDropCount(),
                terms.dutchExpectedDuration().toSeconds(),
                openAt.plus(terms.dutchExpectedDuration())
        );
    }
}
