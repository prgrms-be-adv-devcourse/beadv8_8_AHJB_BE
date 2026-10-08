package com.jjinmak.back.boundedContext.order.app.dto;

import com.jjinmak.back.boundedContext.order.domain.OrderState;

import java.util.UUID;

public record OrderDto(
        Long id,
        UUID winnerId,
        UUID sellerId,
        Long productId,
        Long winningPrice,
        Long deliveryFee,
        OrderState state
) {
}
