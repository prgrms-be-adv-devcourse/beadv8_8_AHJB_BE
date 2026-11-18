package com.jjinmak.back.boundedContext.auction.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AuctionListResponseDto(
        List<AuctionListItemDto> content,
        int page,
        int size,
        long totalElements,
        boolean hasNext,
        LocalDateTime serverTime    // 남은 시간 계산용 (상세와 동일)
) {
}