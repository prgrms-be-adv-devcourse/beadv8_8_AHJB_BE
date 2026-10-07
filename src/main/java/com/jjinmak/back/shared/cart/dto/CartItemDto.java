package com.jjinmak.back.shared.cart.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CartItemDto(
        Long id,
        UUID winnerId,
        UUID sellerId,
        Long productId,
        Long winningPrice,
        Long deliveryFee,
        LocalDateTime winAt,
        LocalDateTime paymentDueAt
) {
}
