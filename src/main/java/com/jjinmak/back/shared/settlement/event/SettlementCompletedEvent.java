package com.jjinmak.back.shared.settlement.event;

import com.jjinmak.back.shared.settlement.dto.SettlementDto;

public record SettlementCompletedEvent(
        SettlementDto settlement
) {
}
