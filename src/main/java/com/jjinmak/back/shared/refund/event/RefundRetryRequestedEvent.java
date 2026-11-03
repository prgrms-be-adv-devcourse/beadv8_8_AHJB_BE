package com.jjinmak.back.shared.refund.event;

import com.jjinmak.back.shared.refund.dto.RefundDto;

import java.time.LocalDateTime;
import java.util.UUID;

public record
RefundRetryRequestedEvent(
        String eventId,
        LocalDateTime occurredAt,
        RefundDto refund
) {
    public static
RefundRetryRequestedEvent of(RefundDto refund) {
        return new
RefundRetryRequestedEvent(UUID.randomUUID().toString(), LocalDateTime.now(), refund);
    }
}