package com.jjinmak.delivery.app.dto;

import com.jjinmak.contracts.delivery.domain.CarrierType;
import com.jjinmak.contracts.delivery.domain.DeliveryStatusCode;

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
