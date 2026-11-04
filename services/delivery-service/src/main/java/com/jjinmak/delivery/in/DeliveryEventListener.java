package com.jjinmak.delivery.in;

import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.contracts.order.event.OrderSendProductEvent;
import com.jjinmak.delivery.app.DeliveryFacade;
import com.jjinmak.delivery.app.event.CallbackRequestedEvent;
import com.jjinmak.delivery.app.event.WebhookRegisterEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.jjinmak.delivery.config.DeliveryAsyncConfig.DELIVERY_TASK_EXECUTOR;

@Component
@RequiredArgsConstructor
public class DeliveryEventListener {

    private final DeliveryFacade deliveryFacade;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(MemberCreatedEvent event){
        deliveryFacade.syncMember(event.member());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(OrderSendProductEvent event){
        deliveryFacade.createDelivery(event);
    }

    @Async(DELIVERY_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(WebhookRegisterEvent event){
        deliveryFacade.registerWebhook(event);
    }

    @Async(DELIVERY_TASK_EXECUTOR)
    @EventListener
    public void handle(CallbackRequestedEvent event){
        deliveryFacade.getDeliveryStatus(event.deliveryId());
    }
}
