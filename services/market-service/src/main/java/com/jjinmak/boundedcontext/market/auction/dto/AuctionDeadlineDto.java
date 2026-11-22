package com.jjinmak.boundedcontext.market.auction.dto;

import java.time.LocalDateTime;

// 곧 끝날 경매의 종료 시각 (종료 타이머 미리 읽기용, #548 4차)
public record AuctionDeadlineDto(Long auctionId, LocalDateTime endAt) {
}
