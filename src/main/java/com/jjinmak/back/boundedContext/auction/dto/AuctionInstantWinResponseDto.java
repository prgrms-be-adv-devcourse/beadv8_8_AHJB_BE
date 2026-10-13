package com.jjinmak.back.boundedContext.auction.dto;

import com.jjinmak.back.boundedContext.auction.domain.AuctionStatus;

import java.time.LocalDateTime;

public record AuctionInstantWinResponseDto (
        Long auctionId,
        Long winningPrice,
        LocalDateTime winAt,
        AuctionStatus status
){

}
