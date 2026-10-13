package com.jjinmak.back.boundedContext.delivery.app.dto;

import com.jjinmak.back.boundedContext.delivery.domain.DeliveryStatusCode;

import java.time.LocalDateTime;

public record DeliveryStatusDto(
        Long orderId,
        DeliveryStatusCode code,
        String message,
        LocalDateTime updatedAt
) {
}
