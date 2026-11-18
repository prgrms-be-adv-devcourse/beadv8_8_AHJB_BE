package com.jjinmak.boundedcontext.market.auction.dto;

import com.jjinmak.boundedcontext.market.auction.domain.AuctionStatus;

import java.time.LocalDateTime;

public record AuctionInstantWinResponseDto (
        Long auctionId,
        Long winningPrice,
        LocalDateTime winAt,
        AuctionStatus status
){

}
