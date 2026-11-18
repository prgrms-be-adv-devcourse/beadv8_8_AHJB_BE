package com.jjinmak.boundedcontext.auction.dto;

import com.jjinmak.boundedcontext.market.shared.domain.AuctionType;

import java.time.LocalDateTime;

public record AuctionCreateDto(
        AuctionType auctionType,
        Long productId,
        int round,                  // 같은 상품의 몇 번째 경매인지 (1부터)
        Long sellerId,
        Long startPrice,
        Long shippingFee,
        LocalDateTime startAt,
        String category,
        Long instantWinPrice,       // ENGLISH
        LocalDateTime endAt,        // ENGLISH
        Long floorPrice,            // DUTCH
        Integer dropRatePercent,    // DUTCH
        Integer dropIntervalSec     // DUTCH
) {
    public static AuctionCreateDto english(Long productId, int round, Long sellerId, Long startPrice, Long shippingFee,
                                           LocalDateTime startAt, String category,
                                           Long instantWinPrice, LocalDateTime endAt) {
        return new AuctionCreateDto(AuctionType.ENGLISH, productId, round, sellerId, startPrice, shippingFee,
                startAt, category, instantWinPrice, endAt, null, null, null);
    }

    public static AuctionCreateDto dutch(Long productId, int round, Long sellerId, Long startPrice, Long shippingFee,
                                         LocalDateTime startAt, String category,
                                         Long floorPrice, Integer dropRatePercent, Integer dropIntervalSec) {
        return new AuctionCreateDto(AuctionType.DUTCH, productId, round, sellerId, startPrice, shippingFee,
                startAt, category, null, null, floorPrice, dropRatePercent, dropIntervalSec);
    }
}
