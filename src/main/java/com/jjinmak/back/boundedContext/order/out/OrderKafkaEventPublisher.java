package com.jjinmak.back.boundedContext.order.out;

import com.jjinmak.back.shared.cart.event.CartPaymentDefaultEvent;
import com.jjinmak.back.shared.order.event.*;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.jjinmak.back.shared.order.event.OrderEventTopics.*;

@Component
@RequiredArgsConstructor
public class OrderKafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(OrderConfirmPurchaseEvent event){
        kafkaTemplate.send(ORDER_CONFIRM_PURCHASE, String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(OrderRefundRequestedEvent event){
        kafkaTemplate.send(ORDER_REFUND_REQUESTED, String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(OrderGroupDeletedEvent event){
        kafkaTemplate.send(ORDER_GROUP_DELETED, String.valueOf(event.orderGroupId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(OrderPaymentSucceedEvent event){
        kafkaTemplate.send(ORDER_PAYMENT_SUCCEED, String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(OrderRefundAcceptedEvent event){
        kafkaTemplate.send(ORDER_REFUND_ACCEPTED, String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(OrderSendProductEvent event){
        kafkaTemplate.send(ORDER_SEND_PRODUCT, String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(CartPaymentDefaultEvent event){
        kafkaTemplate.send(CART_PAYMENT_DEFAULT, String.valueOf(event.auctionId()), event);
    }
}
