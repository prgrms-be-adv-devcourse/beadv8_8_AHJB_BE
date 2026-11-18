package com.jjinmak.boundedcontext.market.auction.dto;

import com.jjinmak.boundedcontext.market.auction.domain.Auction;

// 경매 생성 결과. 상품에 현재 회차의 경매 id를 기록할 때 쓴다.
public record AuctionCreateResultDto(
        Long auctionId
) {
    public static AuctionCreateResultDto from(Auction auction) {
        return new AuctionCreateResultDto(auction.getId());
    }
}
