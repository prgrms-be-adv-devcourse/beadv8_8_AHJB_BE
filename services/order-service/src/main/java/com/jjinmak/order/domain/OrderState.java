package com.jjinmak.order.domain;

public enum OrderState {
    WAITING,
    PAID,
    FAILED,
    SHIPPING,
    SHIPPED,
    CONFIRMED,
    REFUND_REQUESTED,
    REFUNDED,
    DELIVERY_ERROR
}
