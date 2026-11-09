package com.jjinmak.auction.dto;

import java.time.LocalDateTime;
import java.util.List;

public record EnglishAuctionListResponseDto(
        List<EnglishAuctionListItemDto> content,
        int page,
        int size,
        long totalElements,
        boolean hasNext,
        LocalDateTime serverTime    // 남은 시간 계산용 (상세와 동일)
) {
}