package com.jjinmak.boundedcontext.delivery.app;

import com.jjinmak.boundedcontext.delivery.domain.DeliveryOutbox;
import com.jjinmak.boundedcontext.delivery.out.kafka.DeliveryOutboxSender;
import com.jjinmak.boundedcontext.delivery.out.repository.DeliveryOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryOutboxUseCase {

    private final DeliveryOutboxRepository deliveryOutboxRepository;
    private final DeliveryOutboxSender deliveryOutboxSender;

    public List<DeliveryOutbox> findUnpublished(){
        return deliveryOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc();
    }

    public void send(DeliveryOutbox outbox){
        deliveryOutboxSender.send(outbox);
    }

    public void markPublished(Long outboxId){
        deliveryOutboxRepository.findById(outboxId)
                .ifPresent(outbox -> outbox.published(LocalDateTime.now()));
    }
}
