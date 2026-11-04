package com.jjinmak.contracts.refund.event;

import com.jjinmak.back.shared.refund.dto.RefundDto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RefundRejectedEvent(
        String eventId,
        LocalDateTime occurredAt,
        RefundDto refund
) {
    public static RefundRejectedEvent of(RefundDto refund) {
        return new RefundRejectedEvent(UUID.randomUUID().toString(), LocalDateTime.now(), refund);
    }
}
