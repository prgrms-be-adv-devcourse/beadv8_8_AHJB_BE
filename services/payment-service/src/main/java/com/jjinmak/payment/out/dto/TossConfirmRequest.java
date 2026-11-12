package com.jjinmak.payment.out.dto;


public record TossConfirmRequest(
        String paymentKey,
        String orderId,
        long amount
) {
}
