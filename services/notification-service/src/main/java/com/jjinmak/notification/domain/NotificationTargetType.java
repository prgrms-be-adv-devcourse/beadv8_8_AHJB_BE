package com.jjinmak.notification.domain;

// 알림을 눌렀을 때 이동할 화면 종류. (실제 경로는 프론트가 정한다.)
public enum NotificationTargetType {
    AUCTION, ORDER, PRODUCT, CART, SETTLEMENT, MEMBER
}