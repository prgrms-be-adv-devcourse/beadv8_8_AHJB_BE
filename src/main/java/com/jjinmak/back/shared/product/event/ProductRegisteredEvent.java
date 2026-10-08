package com.jjinmak.back.shared.product.event;

import java.util.UUID;

public record ProductRegisteredEvent(
        Long productId,
        UUID sellerId,
        //Long shippingFee,
        Long startPrice,
        Long instantWinPrice,   // 선택
        int duration
) {}