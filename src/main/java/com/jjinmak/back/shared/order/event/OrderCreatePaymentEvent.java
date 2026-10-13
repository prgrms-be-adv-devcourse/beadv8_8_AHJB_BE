package com.jjinmak.back.shared.order.event;

import java.util.List;
import java.util.UUID;

public record OrderCreatePaymentEvent(
        Long orderGroupId,
        Member winner,
        List<OrderItem> orderItems,
        Long totalPrice
) {
    public record Member(Long id, UUID uuid){}

    public record OrderItem(
        Long orderId,
        Long sellerId,
        Long winningPrice,
        Long deliveryFee
    ){}
}
