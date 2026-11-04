package com.jjinmak.contracts.settlement.event;

import com.jjinmak.contracts.settlement.dto.SettlementDto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SettlementRequestedEvent(
        String eventId,
        LocalDateTime occurredAt,
        SettlementDto settlement
) {
    public static SettlementRequestedEvent of(SettlementDto settlement) {
        return new SettlementRequestedEvent(UUID.randomUUID().toString(), LocalDateTime.now(), settlement);
    }
}