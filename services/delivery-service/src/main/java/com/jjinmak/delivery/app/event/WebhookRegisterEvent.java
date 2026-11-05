package com.jjinmak.delivery.app.event;

import com.jjinmak.contracts.delivery.domain.CarrierType;

public record WebhookRegisterEvent(
        Long deliveryId,
        Long orderId,
        CarrierType carrier,
        String waybill
) {
}
