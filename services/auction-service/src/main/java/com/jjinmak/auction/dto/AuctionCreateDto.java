package com.jjinmak.auction.dto;

import com.jjinmak.auction.domain.AuctionType;

import java.time.LocalDateTime;

public record AuctionCreateDto(
        AuctionType auctionType,
        Long productId,
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
    public static AuctionCreateDto english(Long productId, Long sellerId, Long startPrice, Long shippingFee,
                                           LocalDateTime startAt, String category,
                                           Long instantWinPrice, LocalDateTime endAt) {
        return new AuctionCreateDto(AuctionType.ENGLISH, productId, sellerId, startPrice, shippingFee,
                startAt, category, instantWinPrice, endAt, null, null, null);
    }

    public static AuctionCreateDto dutch(Long productId, Long sellerId, Long startPrice, Long shippingFee,
                                         LocalDateTime startAt, String category,
                                         Long floorPrice, Integer dropRatePercent, Integer dropIntervalSec) {
        return new AuctionCreateDto(AuctionType.DUTCH, productId, sellerId, startPrice, shippingFee,
                startAt, category, null, null, floorPrice, dropRatePercent, dropIntervalSec);
    }
}