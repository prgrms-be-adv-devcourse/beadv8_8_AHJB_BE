package com.jjinmak.boundedcontext.market.auction.dto;

import com.jjinmak.boundedcontext.market.auction.domain.Auction;
import com.jjinmak.boundedcontext.market.auction.domain.DutchAuction;
import com.jjinmak.boundedcontext.market.auction.domain.EnglishAuction;

import java.time.LocalDateTime;

// 실시간 구독의 auction 이벤트 (#557). 경매 상세의 auction(모두에게 같은 값)과 serverTime을 그대로 보낸다.
// me(판매자·최고 입찰자 여부)는 사람마다 달라서 보내지 않는다. 필요하면 프론트가 상세 API로 다시 받는다
public record AuctionStreamPayloadDto(
        Object auction,
        LocalDateTime serverTime
) {
    public static AuctionStreamPayloadDto of(Auction auction, LocalDateTime now) {
        Object info = switch (auction) {
            case EnglishAuction english -> EnglishAuctionInfoDto.of(english, now);
            case DutchAuction dutch -> DutchAuctionInfoDto.of(dutch, now);
            default -> throw new IllegalStateException("지원하지 않는 경매 유형입니다. auctionId=" + auction.getId());
        };
        return new AuctionStreamPayloadDto(info, now);
    }
}
