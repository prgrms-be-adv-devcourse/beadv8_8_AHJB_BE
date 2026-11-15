package com.jjinmak.notification.domain;

import lombok.Getter;

import static com.jjinmak.notification.domain.NotificationCategory.*;

@Getter
public enum NotificationType {
    // 구매
    AUCTION_WON_BUYER(BUY, null, null, true),
    PAYMENT_DUE_SOON(BUY, 3600, null, false),
    PAYMENT_DEFAULTED_BUYER(BUY),
    ORDER_SHIPPED(BUY),
    DELIVERY_STATUS(BUY),
    AUTO_CONFIRM_SOON(BUY, 3600, null, false),
    REFUND_BY_SYSTEM(BUY),
    REFUND_COMPLETED_BUYER(BUY),
    REFUND_REJECTED(BUY),
    // 판매
    AUCTION_WON_SELLER(SELL),
    AUCTION_NO_BID(SELL),
    PAYMENT_DEFAULTED_SELLER(SELL),
    ORDER_PAID_SELLER(SELL),
    PURCHASE_CONFIRMED(SELL),
    REFUND_REQUESTED(SELL),
    REFUND_COMPLETED_SELLER(SELL),
    SETTLEMENT_PAID(SELL),
    QNA_QUESTION(SELL),
    // 입찰
    AUCTION_OUTBID(BID, 300, "outbid", true),
    AUCTION_ENDING_SOON(BID, 300, "ending", true),
    // 관심
    WISH_AUCTION_STARTED(INTEREST),
    KEYWORD_AUCTION_STARTED(INTEREST),
    QNA_ANSWERED(INTEREST),
    // 계정
    MEMBER_WARNED(ACCOUNT),
    MEMBER_SANCTIONED(ACCOUNT);

    private final NotificationCategory category;
    private final Integer ttlSeconds;   // null이면 FCM 기본(최대 4주)
    private final String tagSuffix;     // 같은 경매의 알림을 알림창에서 덮어쓸 때만
    private final boolean urgent;

    NotificationType(NotificationCategory category) {
        this(category, null, null, false);
    }

    NotificationType(NotificationCategory category, Integer ttlSeconds, String tagSuffix, boolean urgent) {
        this.category = category;
        this.ttlSeconds = ttlSeconds;
        this.tagSuffix = tagSuffix;
        this.urgent = urgent;
    }
}