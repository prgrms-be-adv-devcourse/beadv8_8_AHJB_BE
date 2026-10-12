package com.jjinmak.back.shared.order.event;

import java.util.UUID;

public record OrderCreatePaymentEvent(
        Long orderGroupId,
        UUID winnerId,
        Long totalPrice
) {
}
