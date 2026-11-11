package com.jjinmak.notification.in;

import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.cart.event.CartPaymentDefaultEvent;
import com.jjinmak.contracts.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.contracts.order.event.*;
import com.jjinmak.contracts.payment.event.WalletReleasedEvent;
import com.jjinmak.contracts.refund.event.RefundCompletedEvent;
import com.jjinmak.contracts.refund.event.RefundRejectedEvent;
import com.jjinmak.notification.app.NotificationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_FAILED;
import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_WON;
import static com.jjinmak.contracts.delivery.event.DeliveryEventTopics.DELIVERY_STATUS_UPDATED;
import static com.jjinmak.contracts.order.event.OrderEventTopics.*;
import static com.jjinmak.contracts.payment.event.PaymentEventTopics.PAYMENT_RELEASED_EVENT;
import static com.jjinmak.contracts.refund.event.RefundEventTopics.REFUND_COMPLETED;
import static com.jjinmak.contracts.refund.event.RefundEventTopics.REFUND_REJECTED;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final String GROUP = "notification";

    private final NotificationFacade notificationFacade;

    @KafkaListener(topics = AUCTION_WON, groupId = GROUP)
    public void handleAuctionWon(AuctionWonEvent event) {
        log(AUCTION_WON, notificationFacade.notifyAuctionWon(event, now()));
    }

    @KafkaListener(topics = AUCTION_FAILED, groupId = GROUP)
    public void handleAuctionFailed(AuctionFailedEvent event) {
        log(AUCTION_FAILED, notificationFacade.notifyAuctionFailed(event, now()));
    }

    @KafkaListener(topics = CART_PAYMENT_DEFAULT, groupId = GROUP)
    public void handlePaymentDefault(CartPaymentDefaultEvent event) {
        log(CART_PAYMENT_DEFAULT, notificationFacade.notifyPaymentDefault(event, now()));
    }

    @KafkaListener(topics = ORDER_REMIND_PAYMENT_DEFAULT, groupId = GROUP)
    public void handleRemindPayment(OrderRemindPaymentEvent event) {
        log(ORDER_REMIND_PAYMENT_DEFAULT, notificationFacade.notifyRemindPayment(event, now()));
    }

    @KafkaListener(topics = ORDER_PAYMENT_SUCCEED, groupId = GROUP)
    public void handlePaymentSucceed(OrderPaymentSucceedEvent event) {
        log(ORDER_PAYMENT_SUCCEED, notificationFacade.notifyPaymentSucceed(event, now()));
    }

    @KafkaListener(topics = ORDER_SEND_PRODUCT, groupId = GROUP)
    public void handleSendProduct(OrderSendProductEvent event) {
        log(ORDER_SEND_PRODUCT, notificationFacade.notifySendProduct(event, now()));
    }

    @KafkaListener(topics = ORDER_CONFIRM_PURCHASE, groupId = GROUP)
    public void handleConfirmPurchase(OrderConfirmPurchaseEvent event) {
        log(ORDER_CONFIRM_PURCHASE, notificationFacade.notifyConfirmPurchase(event, now()));
    }

    @KafkaListener(topics = ORDER_REMIND_AUTO_CONFIRM, groupId = GROUP)
    public void handleRemindAutoConfirm(OrderRemindAutoConfirmEvent event) {
        log(ORDER_REMIND_AUTO_CONFIRM, notificationFacade.notifyRemindAutoConfirm(event, now()));
    }

    @KafkaListener(topics = ORDER_REFUND_REQUESTED, groupId = GROUP)
    public void handleRefundRequested(OrderRefundRequestedEvent event) {
        log(ORDER_REFUND_REQUESTED, notificationFacade.notifyRefundRequested(event, now()));
    }

    @KafkaListener(topics = DELIVERY_STATUS_UPDATED, groupId = GROUP)
    public void handleDeliveryStatus(DeliveryStatusUpdatedEvent event) {
        log(DELIVERY_STATUS_UPDATED, notificationFacade.notifyDeliveryStatus(event, now()));
    }

    @KafkaListener(topics = REFUND_COMPLETED, groupId = GROUP)
    public void handleRefundCompleted(RefundCompletedEvent event) {
        log(REFUND_COMPLETED, notificationFacade.notifyRefundCompleted(event, now()));
    }

    @KafkaListener(topics = REFUND_REJECTED, groupId = GROUP)
    public void handleRefundRejected(RefundRejectedEvent event) {
        log(REFUND_REJECTED, notificationFacade.notifyRefundRejected(event, now()));
    }

    @KafkaListener(topics = PAYMENT_RELEASED_EVENT, groupId = GROUP)
    public void handleSettlementPaid(WalletReleasedEvent event) {
        log(PAYMENT_RELEASED_EVENT, notificationFacade.notifySettlementPaid(event, now()));
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    private void log(String topic, int saved) {
        log.info("알림 저장 topic={}, 저장={}", topic, saved);
    }
}