package com.jjinmak.search.domain;

import java.util.List;

// 검색 결과: 이번 페이지의 문서들 + 조건에 맞는 전체 문서 수
public record ProductSearchResult(
        List<ProductSearchDocument> documents,
        long totalElements
) {
    public static ProductSearchResult empty() {
        return new ProductSearchResult(List.of(), 0);
    }
}
