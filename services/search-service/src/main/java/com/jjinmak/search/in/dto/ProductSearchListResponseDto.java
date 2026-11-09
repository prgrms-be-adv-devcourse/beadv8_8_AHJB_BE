package com.jjinmak.search.in.dto;

import java.util.List;

// 검색 결과 목록. 경매 목록·곧 시작 목록과 같은 형식(content, page, size, totalElements, hasNext)으로 맞춘다.
public record ProductSearchListResponseDto(
        List<ProductSearchItemDto> content,
        int page,
        int size,
        long totalElements,     // 조건에 맞는 전체 상품 수 ("검색 결과 N건")
        boolean hasNext
) {
    public static ProductSearchListResponseDto of(List<ProductSearchItemDto> content, int page, int size, long totalElements) {
        boolean hasNext = (long) (page + 1) * size < totalElements;
        return new ProductSearchListResponseDto(content, page, size, totalElements, hasNext);
    }
}
