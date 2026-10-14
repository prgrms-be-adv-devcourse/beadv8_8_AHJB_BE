package com.jjinmak.back.boundedContext.delivery.app.dto;

import com.jjinmak.back.shared.delivery.domain.CarrierType;

public record WebhookTargetDto(
        Long deliveryId,
        Long orderId,
        CarrierType carrier,
        String waybill
) {
}
