package com.jjinmak.settlement.in.dto;

import com.jjinmak.contracts.settlement.domain.SettlementStatus;

import java.time.LocalDate;

public record SettlementItemDto(
        Long settlementId,
        String productName,
        Long settlementAmount,
        SettlementStatus status,
        LocalDate payoutDate
) {
}