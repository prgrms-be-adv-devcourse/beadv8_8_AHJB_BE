package com.jjinmak.back.shared.product.event;


public record ProductRegisteredEvent(
        Long productId,
        Long sellerId,
        //Long shippingFee,
        Long startPrice,
        Long instantWinPrice,   // 선택
        int duration
) {}