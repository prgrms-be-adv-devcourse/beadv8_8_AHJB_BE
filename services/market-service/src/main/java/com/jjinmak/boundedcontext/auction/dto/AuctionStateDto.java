package com.jjinmak.boundedcontext.auction.dto;

import com.jjinmak.boundedcontext.auction.domain.Auction;
import com.jjinmak.boundedcontext.auction.domain.AuctionStatus;
import com.jjinmak.boundedcontext.auction.domain.DutchAuction;
import com.jjinmak.boundedcontext.auction.domain.FailureReason;

import java.time.LocalDateTime;

// 다른 컨텍스트(상품)가 읽는 경매 상태와 결과
public record AuctionStateDto(
        Long auctionId,
        AuctionStatus status,
        FailureReason failureReason,    // FAILED일 때만 값이 있음
        LocalDateTime endAt             // 영국식은 연장이 반영된 종료 시각, 네덜란드식은 null (경매 시작 이벤트와 같은 규칙)
) {
    public static AuctionStateDto from(Auction auction) {
        return new AuctionStateDto(
                auction.getId(),
                auction.getStatus(),
                auction.getFailureReason(),
                auction instanceof DutchAuction ? null : auction.getEndAt()
        );
    }
}
