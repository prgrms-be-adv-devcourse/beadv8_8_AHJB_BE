package com.jjinmak.notification.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Getter
@RequiredArgsConstructor
public enum NotificationCategory {
    BID(true),       // 입찰
    BUY(true),       // 구매
    SELL(true),      // 판매
    INTEREST(true),  // 관심
    ACCOUNT(false);  // 계정

    private final boolean pushConfigurable;

    public static List<NotificationCategory> pushConfigurables() {
        return Arrays.stream(values())
                .filter(NotificationCategory::isPushConfigurable)
                .toList();
    }
}