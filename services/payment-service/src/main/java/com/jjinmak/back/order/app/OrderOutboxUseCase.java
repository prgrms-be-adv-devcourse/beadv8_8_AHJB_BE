package com.jjinmak.back.order.app;

import com.jjinmak.back.order.domain.OrderOutbox;
import com.jjinmak.back.order.out.kafka.OrderOutBoxSender;
import com.jjinmak.back.order.out.repository.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderOutboxUseCase {

    private final OrderOutboxRepository orderOutboxRepository;
    private final OrderOutBoxSender orderOutBoxSender;

    public List<OrderOutbox> findUnpublished(){
        return orderOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc();
    }

    public void send(OrderOutbox outbox){
        orderOutBoxSender.send(outbox);
    }

    public void markPublished(Long outboxId){
        orderOutboxRepository.findById(outboxId)
                .ifPresent(outbox -> outbox.published(LocalDateTime.now()));
    }
}
