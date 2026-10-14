package com.jjinmak.back.shared.cart.event;

public record CartPaymentDefaultEvent(
    Long auctionId,
    Long productId,
    Long winnerId,
    Long sellerId,
    Long winningPrice,
    Long deliveryFee
) {}
