package com.jjinmak.back.shared.order.dto;

import java.util.UUID;

public record OrderCreatePaymentDto(
        Long orderGroupId,
        UUID winnerId,
        Long totalPrice
) {
}
