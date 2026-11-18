package com.jjinmak.back.boundedContext.settlement.in.dto;

import com.jjinmak.back.shared.settlement.domain.SettlementStatus;

import java.time.LocalDate;

public record SettlementItemDto(
        Long settlementId,
        String productName,
        Long settlementAmount,
        SettlementStatus status,
        LocalDate payoutDate
) {
}