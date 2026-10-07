package com.jjinmak.back.boundedContext.auction.dto;

import com.jjinmak.back.boundedContext.auction.domain.AuctionDuration;

import java.util.UUID;

public record AuctionCreateDto(
        Long productId,
        UUID sellerId,
        Long startPrice,
        Long instantWinPrice,   // 선택
        AuctionDuration duration) {
}
