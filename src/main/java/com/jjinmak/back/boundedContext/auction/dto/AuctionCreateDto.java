package com.jjinmak.back.boundedContext.auction.dto;


import java.time.LocalDateTime;

public record AuctionCreateDto(
        Long productId,
        Long sellerId,
        Long startPrice,
        Long instantWinPrice,   // 선택
        Long shippingFee,
        LocalDateTime endAt
) {
}
