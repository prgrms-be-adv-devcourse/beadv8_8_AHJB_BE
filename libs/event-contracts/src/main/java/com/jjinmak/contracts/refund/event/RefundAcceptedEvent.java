package com.jjinmak.contracts.refund.event;

import com.jjinmak.back.shared.refund.dto.RefundDto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RefundAcceptedEvent(
        String eventId,
        LocalDateTime occurredAt,
        RefundDto refund
) {
    public static RefundAcceptedEvent of(RefundDto refund) {
        return new RefundAcceptedEvent(UUID.randomUUID().toString(), LocalDateTime.now(), refund);
    }
}
