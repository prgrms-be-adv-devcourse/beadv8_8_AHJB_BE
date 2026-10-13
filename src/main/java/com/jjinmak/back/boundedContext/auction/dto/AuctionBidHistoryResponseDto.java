package com.jjinmak.back.boundedContext.auction.dto;

import java.util.List;

public record AuctionBidHistoryResponseDto(
        List<AuctionBidHistoryItemDto> content,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
}
