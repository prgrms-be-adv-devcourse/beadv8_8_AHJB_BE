package com.jjinmak.auction.dto;

import com.jjinmak.auction.domain.EnglishAuction;

public record AuctionViewerDto (
        boolean isSeller,
        boolean isHighestBidder
){
    public static AuctionViewerDto of(EnglishAuction auction, Long memberId){
        return new AuctionViewerDto(
                auction.isSeller(memberId),
                auction.isHighestBidder(memberId)
        );
    }
}
