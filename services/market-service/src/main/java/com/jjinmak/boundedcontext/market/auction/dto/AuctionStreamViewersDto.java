package com.jjinmak.boundedcontext.market.auction.dto;

// 실시간 구독의 viewers 이벤트 (#557). 지금 이 경매를 구독 중인 연결 수 (탭 수 기준, 서버 1대 기준)
public record AuctionStreamViewersDto(
        int count
) {
}
