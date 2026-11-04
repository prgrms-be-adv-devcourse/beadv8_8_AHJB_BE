package com.jjinmak.contracts.settlement.dto;

public record SettlementDto(
        Long settlementId,
        Long orderId,
        Long sellerId,
        Long settlementAmount,
        Long feeAmount
) {
}