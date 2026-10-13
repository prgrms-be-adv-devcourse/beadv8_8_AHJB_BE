package com.jjinmak.back.boundedContext.review.in.dto;

import com.jjinmak.back.boundedContext.review.domain.Review;
import com.jjinmak.back.boundedContext.review.domain.ReviewableOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReviewDto(
        Long reviewId,
        Long orderId,
        Long productId,
        String productName,
        Long writerId,
        String writerNickname,
        Long targetId,
        String targetNickname,
        BigDecimal rating,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReviewDto from(Review review) {
        ReviewableOrder reviewableOrder = review.getReviewableOrder();

        return new ReviewDto(
                review.getId(),
                reviewableOrder.getOrderId(),
                reviewableOrder.getProductId(),
                reviewableOrder.getProductName(),
                review.getWriter().getId(),
                review.getWriter().getNickname(),
                review.getTarget().getId(),
                review.getTarget().getNickname(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
