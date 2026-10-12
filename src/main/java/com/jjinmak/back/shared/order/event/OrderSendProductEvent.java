package com.jjinmak.back.shared.order.event;

import com.jjinmak.back.shared.delivery.domain.CarrierType;

public record OrderSendProductEvent(
        Long orderId,
        Long winnerId,
        Long sellerId,
        CarrierType carrier,
        String waybill
) {
}
