package com.jjinmak.back.boundedContext.delivery.app.dto;

import com.jjinmak.back.shared.delivery.domain.CarrierType;

public record DeliveryResponseDto(
        Long orderId,
        CarrierType carrier,
        String waybill
) {
}
