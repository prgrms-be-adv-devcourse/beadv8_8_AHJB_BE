package com.jjinmak.contracts.auction.event;

import java.time.LocalDateTime;
import java.util.List;

public record AuctionEndingSoonEvent(
        Long auctionId,
        Long productId,
        LocalDateTime endAt,          // 발행 시점의 종료 시각(연장 반영)
        List<Long> bidderIds,         // 이 경매에 입찰한 회원 전원(중복 제거)
        LocalDateTime occurredAt      // 발행 시각. 알림 지연 측정용
) {
    public AuctionEndingSoonEvent {
        bidderIds = bidderIds == null ? List.of() : List.copyOf(bidderIds);
    }
}