package com.jjinmak.contracts.delivery.event;

import com.jjinmak.contracts.delivery.domain.DeliveryStatusCode;

import java.time.LocalDateTime;

public record DeliveryStatusUpdatedEvent(
        Long orderId,
        Long winnerId,
        Long sellerId,
        DeliveryStatusCode beforeStatus,
        DeliveryStatusCode afterStatus,
        LocalDateTime updatedAt
) {
}
