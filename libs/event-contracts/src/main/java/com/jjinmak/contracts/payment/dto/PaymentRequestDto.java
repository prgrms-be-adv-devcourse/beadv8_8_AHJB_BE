package com.jjinmak.contracts.payment.dto;

import java.util.List;


// payMethod      : 포인트로 내고 남은 금액을 낼 결제수단. DEPOSIT(예치금) / TRANSFER(계좌이체 직접 결제) / CARD(카드·간편결제 직접 결제). null 이면 DEPOSIT
// pointUseAmount : 포인트로 낼 금액. 0 이상, totalPrice 이하. 포인트 잔액과 수단 검증은 결제가 한다
public record PaymentRequestDto(
        Long orderGroupId,
        Long buyerId,
        long totalPrice,
        List<Item> items,
        String payMethod,
        long pointUseAmount
) {
    // 결제수단을 보내지 않는 기존 호출. 전액 예치금 결제(포인트 0)로 동작한다
    public PaymentRequestDto(Long orderGroupId, Long buyerId, long totalPrice, List<Item> items) {
        this(orderGroupId, buyerId, totalPrice, items, null, 0L);
    }

    public record Item(
            Long orderId,
            Long sellerId,
            long winningPrice,
            long deliveryFee
    ) {}
}
