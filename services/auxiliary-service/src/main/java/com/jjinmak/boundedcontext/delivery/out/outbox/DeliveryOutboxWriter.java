package com.jjinmak.boundedcontext.delivery.out.outbox;

import com.jjinmak.boundedcontext.delivery.domain.DeliveryOutbox;
import com.jjinmak.boundedcontext.delivery.out.repository.DeliveryOutboxRepository;
import com.jjinmak.contracts.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.contracts.delivery.event.DeliveryStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import static com.jjinmak.contracts.delivery.event.DeliveryEventTopics.DELIVERY_CREATED;
import static com.jjinmak.contracts.delivery.event.DeliveryEventTopics.DELIVERY_STATUS_UPDATED;

@Component
@RequiredArgsConstructor
public class DeliveryOutboxWriter {

    private final DeliveryOutboxRepository deliveryOutboxRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(DeliveryCreatedEvent event){
        save(DELIVERY_CREATED, event.orderId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(DeliveryStatusUpdatedEvent event){
        save(DELIVERY_STATUS_UPDATED, event.orderId(), event);
    }

    private void save(String topic, Long key, Object event){
        deliveryOutboxRepository.save(new DeliveryOutbox(topic, String.valueOf(key), objectMapper.writeValueAsString(event)));
    }
}
