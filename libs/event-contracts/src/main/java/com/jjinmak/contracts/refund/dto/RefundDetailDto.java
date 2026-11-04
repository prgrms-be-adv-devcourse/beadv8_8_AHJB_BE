package com.jjinmak.contracts.refund.dto;

import com.jjinmak.back.shared.refund.domain.RefundStatus;

import java.time.LocalDateTime;

public record RefundDetailDto(
        Long refundId,
        Long orderId,
        Long productId,
        Long amount,
        RefundStatus status,
        LocalDateTime requestedAt,
        LocalDateTime processedAt
) {
}
