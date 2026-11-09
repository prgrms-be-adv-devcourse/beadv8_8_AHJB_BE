package com.jjinmak.auction.dto;

import com.jjinmak.auction.domain.EnglishAuction;

public record EnglishAuctionViewerDto (
        boolean isSeller,
        boolean isHighestBidder
){
    public static EnglishAuctionViewerDto of(EnglishAuction auction, Long memberId){
        return new EnglishAuctionViewerDto(
                auction.isSeller(memberId),
                auction.isHighestBidder(memberId)
        );
    }
}
