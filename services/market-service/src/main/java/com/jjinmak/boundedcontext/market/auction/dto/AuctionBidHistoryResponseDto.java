package com.jjinmak.boundedcontext.market.auction.dto;

import java.util.List;

public record AuctionBidHistoryResponseDto(
        List<AuctionBidHistoryItemDto> content,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
}
