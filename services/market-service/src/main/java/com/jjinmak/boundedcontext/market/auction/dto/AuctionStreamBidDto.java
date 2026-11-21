package com.jjinmak.boundedcontext.market.auction.dto;

import java.time.LocalDateTime;

// 실시간 구독의 bid 이벤트 (#557). 입찰 내역 한 행과 같은 모양에서 isMine만 뺐다 (사람마다 다른 값).
// 프론트는 내 입찰인지를 자기 입찰 응답(bidId)으로 표시한다
public record AuctionStreamBidDto(
        Long bidId,
        String bidderNickname,
        Long bidPrice,
        LocalDateTime bidAt
) {
}
