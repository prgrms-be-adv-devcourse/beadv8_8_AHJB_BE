package com.jjinmak.back.boundedContext.delivery.app.event;

import com.jjinmak.back.shared.delivery.domain.CarrierType;

public record WebhookRegisterEvent(
        Long deliveryId,
        CarrierType carrier,
        String waybill
) {
}
