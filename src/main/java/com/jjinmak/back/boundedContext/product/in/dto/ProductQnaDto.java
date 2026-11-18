package com.jjinmak.back.boundedContext.product.in.dto;

import com.jjinmak.back.boundedContext.product.domain.ProductQna;

import java.time.LocalDateTime;
import java.util.List;

public record ProductQnaDto(
        Long qnaId,
        Long parentId,          // 질문이면 null
        String content,         // 삭제된 Q&A면 null
        Long writerId,
        String writerNickname,
        boolean isSeller,       // 작성자가 상품 판매자인지
        boolean isDeleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ProductQnaDto> replies
) {
    public static ProductQnaDto of(ProductQna qna, Long sellerId, List<ProductQnaDto> replies) {
        return new ProductQnaDto(
                qna.getId(),
                qna.isQuestion() ? null : qna.getParent().getId(),
                qna.isDeleted() ? null : qna.getContent(),
                qna.getWriter().getId(),
                qna.getWriter().getNickname(),
                qna.isWrittenBy(sellerId),
                qna.isDeleted(),
                qna.getCreatedAt(),
                qna.getUpdatedAt(),
                replies
        );
    }

    public static ProductQnaDto of(ProductQna qna, Long sellerId) {
        return of(qna, sellerId, List.of());
    }
}
