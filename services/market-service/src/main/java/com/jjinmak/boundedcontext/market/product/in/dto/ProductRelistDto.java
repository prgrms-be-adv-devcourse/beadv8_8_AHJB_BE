package com.jjinmak.boundedcontext.market.product.in.dto;

// 재등록 결과. 이전 회차 경매 id(다시 열 수 있는 상태인지 경매에서 확인)와 새 회차의 판매 조건.
public record ProductRelistDto(
        Long previousAuctionId,
        ProductAuctionOpenDto open
) {
}
