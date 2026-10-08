package com.jjinmak.back.shared.refund.dto;

import com.jjinmak.back.boundedContext.refund.domain.RefundStatus;

import java.time.LocalDateTime;

public record RefundDto(
        Long refundId,
        Long orderId,
        RefundStatus status,
        Long amount,
        LocalDateTime requestedAt
) {
}
