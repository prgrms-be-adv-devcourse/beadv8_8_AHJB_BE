package com.jjinmak.search.domain;

// 검색 결과 정렬 기준
public enum ProductSearchSort {
    RELEVANCE,      // 정확도순 (검색어와 잘 맞는 순, 검색어가 있을 때 기본값)
    LATEST,         // 최신 등록순 (검색어가 없을 때 기본값)
    ENDING_SOON,    // 경매 마감 임박순
    PRICE_ASC,      // 시작가 낮은순
    PRICE_DESC      // 시작가 높은순
}
