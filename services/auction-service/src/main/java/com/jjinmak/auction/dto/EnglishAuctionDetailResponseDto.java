package com.jjinmak.auction.dto;

import com.jjinmak.auction.domain.EnglishAuction;

import java.time.LocalDateTime;

public record EnglishAuctionDetailResponseDto(
        AuctionInfoDto auction,
        AuctionViewerDto me,
        LocalDateTime serverTime
) implements AuctionDetailResponseDto {

    public static EnglishAuctionDetailResponseDto of(EnglishAuction auction, Long memberId, LocalDateTime now) {
        return new EnglishAuctionDetailResponseDto(
                AuctionInfoDto.of(auction, now), AuctionViewerDto.of(auction, memberId), now);
    }
}