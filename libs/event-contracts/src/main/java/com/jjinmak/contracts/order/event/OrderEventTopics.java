package com.jjinmak.contracts.order.event;

public final class OrderEventTopics {

    public static final String ORDER_CONFIRM_PURCHASE = "order.confirm-purchase";
    public static final String ORDER_REFUND_REQUESTED = "order.refund-requested";
    public static final String ORDER_GROUP_DELETED = "order.group-deleted";
    public static final String ORDER_PAYMENT_SUCCEED = "order.payment-succeed";
    public static final String ORDER_REFUND_ACCEPTED = "order.refund-accepted";
    public static final String ORDER_SEND_PRODUCT = "order.send-product";

    public static final String CART_PAYMENT_DEFAULT = "order.cart-payment-default";
    public static final String ORDER_REMIND_PAYMENT_DEFAULT = "order.remind-payment-default";
}
