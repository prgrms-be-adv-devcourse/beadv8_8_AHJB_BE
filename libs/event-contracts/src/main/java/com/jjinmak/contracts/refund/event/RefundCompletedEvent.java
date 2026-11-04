package com.jjinmak.contracts.refund.event;

import com.jjinmak.back.shared.refund.dto.RefundDto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RefundCompletedEvent(
        String eventId,
        LocalDateTime occurredAt,
        RefundDto refund
) {
    public static RefundCompletedEvent of(RefundDto refund) {
        return new RefundCompletedEvent(UUID.randomUUID().toString(), LocalDateTime.now(), refund);
    }
}

