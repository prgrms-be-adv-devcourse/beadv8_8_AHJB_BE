package com.jjinmak.back.boundedContext.auction.dto;


public record AuctionCreateDto(
        Long productId,
        Long sellerId,
        Long startPrice,
        Long instantWinPrice,   // 선택
        int duration) {
}
