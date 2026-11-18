package com.jjinmak.boundedcontext.auction.dto;

import com.jjinmak.boundedcontext.auction.domain.Auction;
import com.jjinmak.boundedcontext.auction.domain.DutchAuction;

import java.time.LocalDateTime;

// 경매 생성 결과. 상품에 현재 회차의 경매 id와 종료 시각을 기록할 때 쓴다.
public record AuctionCreateResultDto(
        Long auctionId,
        LocalDateTime endAt     // 네덜란드식은 null (경매 시작 이벤트와 같은 규칙)
) {
    public static AuctionCreateResultDto from(Auction auction) {
        return new AuctionCreateResultDto(
                auction.getId(),
                auction instanceof DutchAuction ? null : auction.getEndAt()
        );
    }
}
