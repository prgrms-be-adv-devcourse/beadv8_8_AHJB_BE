package com.jjinmak.order.app.dto;

import com.jjinmak.order.domain.OrderState;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderDto(
        Long id,
        UUID winnerId,
        UUID sellerId,
        Long productId,
        Long winningPrice,
        Long deliveryFee,
        OrderState state,
        LocalDateTime confirmExpiredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
