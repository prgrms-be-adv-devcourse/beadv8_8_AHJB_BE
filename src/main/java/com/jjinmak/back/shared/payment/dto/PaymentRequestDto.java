package com.jjinmak.back.shared.payment.dto;

import java.util.List;

/**
 * 주문하기 시 주문 → 결제로 넘기는 주문 묶음 정보. (기존 OrderCreatePaymentEvent 와 같은 모양)
 */
public record PaymentRequestDto(
        Long orderGroupId,
        Long buyerId,
        long totalPrice,
        List<Item> items
) {
    public record Item(
            Long orderId,
            Long sellerId,
            long winningPrice,
            long deliveryFee
    ) {}
}
