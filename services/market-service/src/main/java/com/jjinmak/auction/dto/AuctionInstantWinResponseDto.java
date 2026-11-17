package com.jjinmak.auction.dto;

import com.jjinmak.auction.domain.AuctionStatus;

import java.time.LocalDateTime;

public record AuctionInstantWinResponseDto (
        Long auctionId,
        Long winningPrice,
        LocalDateTime winAt,
        AuctionStatus status
){

}
