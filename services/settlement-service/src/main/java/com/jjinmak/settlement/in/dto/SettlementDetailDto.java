package com.jjinmak.settlement.in.dto;

import com.jjinmak.contracts.settlement.domain.SettlementStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SettlementDetailDto(
        Long settlementId,
        Long orderId,
        Long productId,
        String productName,
        Long winningPrice,
        Long deliveryFee,
        Long feeAmount,
        Long settlementAmount,
        SettlementStatus status,
        LocalDateTime confirmedAt,
        LocalDate payoutDate,
        LocalDateTime completedAt
) {
}