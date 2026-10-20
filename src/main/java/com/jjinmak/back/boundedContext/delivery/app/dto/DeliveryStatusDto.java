package com.jjinmak.back.boundedContext.delivery.app.dto;

import com.jjinmak.back.shared.delivery.domain.CarrierType;
import com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode;

import java.time.LocalDateTime;

public record DeliveryStatusDto(
        Long orderId,
        CarrierType carrier,
        String waybill,
        DeliveryStatusCode code,
        String message,
        LocalDateTime updatedAt
) {
}
