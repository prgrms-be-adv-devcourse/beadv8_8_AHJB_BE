package com.jjinmak.boundedcontext.auction.dto;

import com.jjinmak.boundedcontext.auction.domain.AuctionStatus;

import java.time.LocalDateTime;

public record AuctionInstantWinResponseDto (
        Long auctionId,
        Long winningPrice,
        LocalDateTime winAt,
        AuctionStatus status
){

}
