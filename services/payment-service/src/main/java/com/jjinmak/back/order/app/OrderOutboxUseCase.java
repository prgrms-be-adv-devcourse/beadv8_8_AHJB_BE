package com.jjinmak.back.order.app;

import com.jjinmak.back.order.app.dto.OutboxSendResult;
import com.jjinmak.back.order.domain.OrderOutbox;
import com.jjinmak.back.order.out.kafka.OrderOutBoxSender;
import com.jjinmak.back.order.out.repository.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
public class OrderOutboxUseCase {

    private final OrderOutboxRepository orderOutboxRepository;
    private final OrderOutBoxSender orderOutBoxSender;

    public List<OrderOutbox> findUnpublished(){
        return orderOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc();
    }

    public OutboxSendResult sendAll(List<OrderOutbox> outboxes){
        Map<OrderOutbox, CompletableFuture<?>> sending = new LinkedHashMap<>();
        Map<OrderOutbox, Throwable> failures = new LinkedHashMap<>();

        for (OrderOutbox outbox : outboxes){
            try {
                sending.put(outbox, orderOutBoxSender.send(outbox));
            } catch (RuntimeException e){
                failures.put(outbox, e);
                break;
            }
        }

        List<Long> sentIds = new ArrayList<>();
        sending.forEach((outbox, future) -> {
            try {
                future.join();
                sentIds.add(outbox.getId());
            } catch (CompletionException e){
                failures.put(outbox, e.getCause());
            }
        });

        return new OutboxSendResult(sentIds, failures);
    }

    public void markPublished(List<Long> outboxIds){
        if (outboxIds.isEmpty())
            return;

        orderOutboxRepository.markPublished(outboxIds, LocalDateTime.now());
    }
}
