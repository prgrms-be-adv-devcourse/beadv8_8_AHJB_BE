package com.jjinmak.back.shared.product.event;

import java.time.LocalDateTime;

public record ProductRegisteredEvent(
        Long productId,
        Long sellerId,
        //Long shippingFee,
        Long startPrice,
        Long instantWinPrice,   // 선택
        int duration,           // 1, 3, 7일 (직접 지정이면 0)
        LocalDateTime customEndAt   // 종료 시간 직접 지정 시에만 값이 있음
) {}
