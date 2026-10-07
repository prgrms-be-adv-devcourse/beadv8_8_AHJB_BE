package com.jjinmak.back.boundedContext.order.domain;

public enum OrderState {
    WAITING,
    PAID,
    FAILED,
    SHIPPING,
    SHIPPED,
    CONFIRMED,
    REFUND_REQUESTED,
    REFUNDED
}
