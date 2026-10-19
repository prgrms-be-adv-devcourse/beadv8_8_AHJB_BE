package com.jjinmak.back.shared.settlement.dto;

import com.jjinmak.back.shared.settlement.domain.SettlementStatus;

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