package com.jjinmak.back.boundedContext.review.in.dto;

import com.jjinmak.back.boundedContext.review.domain.ReviewableOrder;

import java.time.LocalDateTime;

public record ReviewableOrderDto(
        Long orderId,
        Long productId,
        String productName,
        Long sellerId,
        String sellerNickname,
        Long winningPrice,
        LocalDateTime confirmedAt,
        LocalDateTime reviewDeadline
) {
    public static ReviewableOrderDto from(ReviewableOrder reviewableOrder) {
        return new ReviewableOrderDto(
                reviewableOrder.getOrderId(),
                reviewableOrder.getProductId(),
                reviewableOrder.getProductName(),
                reviewableOrder.getSeller().getId(),
                reviewableOrder.getSeller().getNickname(),
                reviewableOrder.getWinningPrice(),
                reviewableOrder.getConfirmedAt(),
                reviewableOrder.getReviewDeadline()
        );
    }
}
