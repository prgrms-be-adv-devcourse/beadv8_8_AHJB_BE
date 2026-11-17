package com.jjinmak.auction.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DutchAuctionListResponseDto(
        List<DutchAuctionListItemDto> content,
        int page,
        int size,
        long totalElements,
        boolean hasNext,
        LocalDateTime serverTime    // nextDropAt 카운트다운 보정용
) {
}