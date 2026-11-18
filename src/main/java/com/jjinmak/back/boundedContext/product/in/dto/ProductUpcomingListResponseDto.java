package com.jjinmak.back.boundedContext.product.in.dto;

import java.time.LocalDateTime;
import java.util.List;

// 경매 목록 응답(AuctionListResponseDto)과 같은 형식. 프론트가 같은 목록 화면에서 탭만 바꿔 쓴다.
public record ProductUpcomingListResponseDto(
        List<ProductUpcomingItemDto> content,
        int page,
        int size,
        long totalElements,
        boolean hasNext,
        LocalDateTime serverTime    // 시작까지 남은 시간 계산용
) {
}
