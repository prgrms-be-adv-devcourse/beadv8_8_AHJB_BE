package com.jjinmak.auction.dto;

import com.jjinmak.auction.domain.DutchAuction;

import java.time.LocalDateTime;

public record DutchAuctionDetailResponseDto(
        DutchAuctionInfoDto auction,
        DutchAuctionViewerDto me,
        LocalDateTime serverTime
) implements AuctionDetailResponseDto {

    public static DutchAuctionDetailResponseDto of(DutchAuction auction, Long memberId, LocalDateTime now) {
        return new DutchAuctionDetailResponseDto(
                DutchAuctionInfoDto.of(auction, now), DutchAuctionViewerDto.of(auction, memberId), now);
    }
}