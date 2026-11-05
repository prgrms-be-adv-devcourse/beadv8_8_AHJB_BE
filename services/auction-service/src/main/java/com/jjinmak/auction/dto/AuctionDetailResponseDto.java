package com.jjinmak.auction.dto;

import java.time.LocalDateTime;

public record AuctionDetailResponseDto(
        AuctionInfoDto auction,
        AuctionViewerDto me,
        LocalDateTime serverTime
) {
}
