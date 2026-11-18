package com.jjinmak.back.boundedContext.payment.app.dto;

import java.util.List;

/**
 * 주문 결제의 홀딩 요청. paymentId 는 Payment(주문 묶음 결제) 의 id 이며 Holding.paymentId 에 그대로 저장된다.
 * 같은 paymentId 로 두 번 들어오면 두 번째는 아무 것도 하지 않는다 (멱등).
 */
public record HoldDto(
        Long paymentId,
        Long holderId,
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