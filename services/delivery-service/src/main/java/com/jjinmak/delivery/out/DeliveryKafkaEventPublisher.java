package com.jjinmak.delivery.out;

import com.jjinmak.contracts.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.contracts.delivery.event.DeliveryStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.jjinmak.contracts.delivery.event.DeliveryEventTopics.DELIVERY_CREATED;
import static com.jjinmak.contracts.delivery.event.DeliveryEventTopics.DELIVERY_STATUS_UPDATED;

@Component
@RequiredArgsConstructor
public class DeliveryKafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DeliveryCreatedEvent event){
        kafkaTemplate.send(DELIVERY_CREATED, String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DeliveryStatusUpdatedEvent event){
        kafkaTemplate.send(DELIVERY_STATUS_UPDATED, String.valueOf(event.orderId()), event);
    }
}
