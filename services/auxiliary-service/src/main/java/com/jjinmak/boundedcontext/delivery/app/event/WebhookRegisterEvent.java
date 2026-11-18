package com.jjinmak.boundedcontext.delivery.app.event;

import com.jjinmak.contracts.delivery.domain.CarrierType;

import java.util.UUID;

public record WebhookRegisterEvent(
        Long deliveryId,
        Long orderId,
        CarrierType carrier,
        String waybill,
        UUID callbackToken
) {
}
