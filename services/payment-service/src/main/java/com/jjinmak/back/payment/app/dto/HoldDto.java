package com.jjinmak.back.payment.app.dto;

import com.jjinmak.back.payment.domain.PaymentShare;

import java.util.List;

/**
 * share 는 결제 총액을 포인트 · 예치금 · 직접 결제로 각각 얼마씩 내는지다 (합 == totalPrice).
 * 주문별 몫은 홀딩을 만들 때 주문 금액에 비례해 나눈다.
 */
public record HoldDto(
        Long paymentId,
        Long holderId,
        long totalPrice,
        List<Item> items,
        PaymentShare share
) {
    // 전액 예치금 결제
    public HoldDto(Long paymentId, Long holderId, long totalPrice, List<Item> items) {
        this(paymentId, holderId, totalPrice, items, PaymentShare.depositOnly(totalPrice));
    }

    public record Item(
            Long orderId,
            Long sellerId,
            long winningPrice,
            long deliveryFee
    ) {}
}
