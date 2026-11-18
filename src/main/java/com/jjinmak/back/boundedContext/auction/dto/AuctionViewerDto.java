package com.jjinmak.back.boundedContext.auction.dto;

import com.jjinmak.back.boundedContext.auction.domain.Auction;

public record AuctionViewerDto (
        boolean isSeller,
        boolean isHighestBidder
){
    public static AuctionViewerDto of(Auction auction,Long memberId){
        return new AuctionViewerDto(
                auction.isSeller(memberId),
                auction.isHighestBidder(memberId)
        );
    }
}
