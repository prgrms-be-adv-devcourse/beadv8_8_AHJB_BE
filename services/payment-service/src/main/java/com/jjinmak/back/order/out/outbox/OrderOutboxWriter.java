package com.jjinmak.back.order.out.outbox;

import com.jjinmak.back.order.domain.OrderOutbox;
import com.jjinmak.back.order.out.repository.OrderOutboxRepository;
import com.jjinmak.contracts.cart.event.CartPaymentDefaultEvent;
import com.jjinmak.contracts.order.event.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import static com.jjinmak.contracts.order.event.OrderEventTopics.*;

@Component
@RequiredArgsConstructor
public class OrderOutboxWriter {

    private final OrderOutboxRepository orderOutboxRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(OrderConfirmPurchaseEvent event){
        save(ORDER_CONFIRM_PURCHASE, event.orderId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(OrderRefundRequestedEvent event){
        save(ORDER_REFUND_REQUESTED, event.orderId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(OrderGroupDeletedEvent event){
        save(ORDER_GROUP_DELETED, event.orderGroupId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(OrderPaymentSucceedEvent event){
        save(ORDER_PAYMENT_SUCCEED, event.orderId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(OrderRefundAcceptedEvent event){
        save(ORDER_REFUND_ACCEPTED, event.orderId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(OrderSendProductEvent event){
        save(ORDER_SEND_PRODUCT, event.orderId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(CartPaymentDefaultEvent event){
        save(CART_PAYMENT_DEFAULT, event.auctionId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(OrderRemindPaymentEvent event){
        save(ORDER_REMIND_PAYMENT_DEFAULT, event.cartItemId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(OrderRemindAutoConfirmEvent event){
        save(ORDER_REMIND_AUTO_CONFIRM, event.orderId(), event);
    }

    private void save(String topic, Long key, Object event){
        orderOutboxRepository.save(new OrderOutbox(topic, String.valueOf(key), objectMapper.writeValueAsString(event)));
    }
}
