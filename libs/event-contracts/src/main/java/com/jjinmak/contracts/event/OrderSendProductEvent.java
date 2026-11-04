package com.jjinmak.contracts.event;

import com.jjinmak.contracts.delivery.domain.CarrierType;

public record OrderSendProductEvent(
        Long orderId,
        Long winnerId,
        Long sellerId,
        CarrierType carrier,
        String waybill
) {
}
