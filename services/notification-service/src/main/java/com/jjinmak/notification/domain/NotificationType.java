package com.jjinmak.notification.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static com.jjinmak.notification.domain.NotificationCategory.*;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    // 구매
    AUCTION_WON_BUYER(BUY),
    PAYMENT_DUE_SOON(BUY),
    PAYMENT_DEFAULTED_BUYER(BUY),
    ORDER_SHIPPED(BUY),
    DELIVERY_STATUS(BUY),
    AUTO_CONFIRM_SOON(BUY),
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
    AUCTION_OUTBID(BID),
    AUCTION_ENDING_SOON(BID),
    // 관심
    WISH_AUCTION_STARTED(INTEREST),
    KEYWORD_AUCTION_STARTED(INTEREST),
    QNA_ANSWERED(INTEREST),
    // 계정
    MEMBER_WARNED(ACCOUNT),
    MEMBER_SANCTIONED(ACCOUNT);

    private final NotificationCategory category;
}