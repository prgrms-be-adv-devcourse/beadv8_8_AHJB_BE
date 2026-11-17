package com.jjinmak.boundedContext.delivery.in;

import com.jjinmak.boundedContext.delivery.app.DeliveryFacade;
import com.jjinmak.boundedContext.delivery.app.event.CallbackRequestedEvent;
import com.jjinmak.boundedContext.delivery.app.event.WebhookRegisterEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.jjinmak.boundedContext.delivery.config.DeliveryAsyncConfig.DELIVERY_TASK_EXECUTOR;

@Component
@RequiredArgsConstructor
public class DeliveryEventListener {

    private final DeliveryFacade deliveryFacade;

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
