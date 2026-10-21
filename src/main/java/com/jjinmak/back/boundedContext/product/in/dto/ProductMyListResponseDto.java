package com.jjinmak.back.boundedContext.product.in.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ProductMyListResponseDto(
        List<ProductMyItemDto> content,
        LocalDateTime serverTime    // 남은 시간 계산용 (경매 목록과 동일)
) {
}
