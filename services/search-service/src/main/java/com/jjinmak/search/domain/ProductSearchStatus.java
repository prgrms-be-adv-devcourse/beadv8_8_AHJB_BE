package com.jjinmak.search.domain;

// 검색 결과에 보여줄 상품 상태
public enum ProductSearchStatus {
    SCHEDULED,      // 곧 시작 (등록 후 경매 시작 전, 찜 가능)
    IN_PROGRESS,    // 경매 진행 중
    SOLD,           // 낙찰 (확정, 이후 바뀌지 않음)
    FAILED          // 유찰 (확정, 이후 바뀌지 않음)
}
