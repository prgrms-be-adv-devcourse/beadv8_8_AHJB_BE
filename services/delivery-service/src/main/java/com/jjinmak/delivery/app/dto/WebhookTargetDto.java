package com.jjinmak.delivery.app.dto;

import com.jjinmak.contracts.delivery.domain.CarrierType;

public record WebhookTargetDto(
        Long deliveryId,
        Long orderId,
        CarrierType carrier,
        String waybill
) {
}
