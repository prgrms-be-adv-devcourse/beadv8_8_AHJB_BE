package com.jjinmak.boundedcontext.market.in.dto;

// 재등록 응답 (프론트 ReopenResponse)
public record MarketRelistResponseDto(
        Long auctionId,
        int round
) {
}
