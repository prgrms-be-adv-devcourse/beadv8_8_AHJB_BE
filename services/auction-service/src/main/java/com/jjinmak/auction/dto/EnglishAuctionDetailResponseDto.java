package com.jjinmak.auction.dto;

import com.jjinmak.auction.domain.EnglishAuction;

import java.time.LocalDateTime;

public record EnglishAuctionDetailResponseDto(
        EnglishAuctionInfoDto auction,
        EnglishAuctionViewerDto me,
        LocalDateTime serverTime
) implements AuctionDetailResponseDto {

    public static EnglishAuctionDetailResponseDto of(EnglishAuction auction, Long memberId, LocalDateTime now) {
        return new EnglishAuctionDetailResponseDto(
                EnglishAuctionInfoDto.of(auction, now), EnglishAuctionViewerDto.of(auction, memberId), now);
    }
}