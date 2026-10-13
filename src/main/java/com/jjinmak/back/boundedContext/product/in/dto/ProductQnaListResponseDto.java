package com.jjinmak.back.boundedContext.product.in.dto;

import java.util.List;

public record ProductQnaListResponseDto(
        // 일반 조회: 질문 목록(답글은 replies에 포함) / 폴링 조회: 새로 작성된 질문·답글 (parentId로 구분)
        List<ProductQnaDto> qnas,
        boolean hasNext,
        // 이 상품의 가장 최근 Q&A id. 다음 폴링 때 after로 보낸다. (Q&A가 없으면 null)
        Long latestQnaId
) {
}
