package com.jjinmak.boundedcontext.delivery.app.dto;

import com.jjinmak.contracts.delivery.domain.CarrierType;

import java.util.UUID;

public record WebhookTargetDto(
        Long deliveryId,
        Long orderId,
        CarrierType carrier,
        String waybill,
        UUID callbackToken
) {
}
