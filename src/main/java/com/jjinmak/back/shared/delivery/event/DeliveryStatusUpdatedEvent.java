package com.jjinmak.back.shared.delivery.event;

import com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode;

import java.time.LocalDateTime;

public record DeliveryStatusUpdatedEvent(
        Long orderId,
        DeliveryStatusCode beforeStatus,
        DeliveryStatusCode afterStatus,
        LocalDateTime updatedAt
) {
}
