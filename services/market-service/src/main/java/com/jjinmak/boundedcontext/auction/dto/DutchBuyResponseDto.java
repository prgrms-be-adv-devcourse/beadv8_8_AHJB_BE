package com.jjinmak.boundedcontext.auction.dto;

import com.jjinmak.boundedcontext.auction.domain.AuctionStatus;

import java.time.LocalDateTime;

public record DutchBuyResponseDto(
        Long auctionId,
        Long winningPrice,
        LocalDateTime winAt,
        AuctionStatus status
) {
}