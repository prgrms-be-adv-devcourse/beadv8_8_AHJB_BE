package com.jjinmak.boundedcontext.market.auction.dto;

import com.jjinmak.boundedcontext.market.auction.domain.DutchAuction;

public record DutchAuctionViewerDto(boolean isSeller) {
    public static DutchAuctionViewerDto of(DutchAuction auction, Long memberId) {
        return new DutchAuctionViewerDto(auction.isSeller(memberId));
    }
}