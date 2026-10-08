package com.jjinmak.back.boundedContext.product.in.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuctionDuration {
    DAYS_1(1),
    DAYS_3(3),
    DAYS_7(7),
    CUSTOM(0); // 종료 시간 직접 지정 (customEndAt 사용)

    private final int days;
}
