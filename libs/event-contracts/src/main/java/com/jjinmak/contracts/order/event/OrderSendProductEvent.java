package com.jjinmak.contracts.order.event;

import com.jjinmak.contracts.delivery.domain.CarrierType;

public record OrderSendProductEvent(
        Long orderId,
        Long winnerId,
        Long sellerId,
        CarrierType carrier,
        String waybill
) {
}
