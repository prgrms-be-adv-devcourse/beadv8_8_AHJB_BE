package com.jjinmak.back.boundedContext.delivery.in;

import com.jjinmak.back.boundedContext.delivery.app.DeliveryFacade;
import com.jjinmak.back.boundedContext.delivery.app.event.CallbackRequestedEvent;
import com.jjinmak.back.boundedContext.delivery.app.event.WebhookRegisterEvent;
import com.jjinmak.back.shared.member.event.MemberCreatedEvent;
import com.jjinmak.back.shared.order.event.OrderSendProductEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(WebhookRegisterEvent event){
        deliveryFacade.registerWebhook(event);
    }

    @Async
    @EventListener
    public void handle(CallbackRequestedEvent event){
        deliveryFacade.getDeliveryStatus(event.deliveryId());
    }
}
