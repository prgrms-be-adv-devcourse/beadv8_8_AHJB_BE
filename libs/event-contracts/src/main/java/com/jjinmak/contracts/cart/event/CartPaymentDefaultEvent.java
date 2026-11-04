package com.jjinmak.contracts.cart.event;

public record CartPaymentDefaultEvent(
    Long auctionId,
    Long productId,
    Long winnerId,
    Long sellerId,
    Long winningPrice,
    Long deliveryFee
) {}
