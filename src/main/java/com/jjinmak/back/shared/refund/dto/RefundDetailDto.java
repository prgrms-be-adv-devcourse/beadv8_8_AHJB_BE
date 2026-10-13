package com.jjinmak.back.shared.refund.dto;

import com.jjinmak.back.boundedContext.refund.domain.RefundReason;
import com.jjinmak.back.boundedContext.refund.domain.RefundStatus;

import java.time.LocalDateTime;

public record RefundDetailDto(
        Long refundId,
        Long orderId,
        RefundReason reason,
        String detail,
        Long amount,
        RefundStatus status,
        String rejectReason,
        LocalDateTime requestedAt,
        LocalDateTime processedAt
) {
}
