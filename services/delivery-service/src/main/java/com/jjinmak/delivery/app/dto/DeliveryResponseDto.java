package com.jjinmak.delivery.app.dto;

import com.jjinmak.contracts.delivery.domain.CarrierType;

public record DeliveryResponseDto(
        Long orderId,
        CarrierType carrier,
        String waybill
) {
}
