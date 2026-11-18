package com.jjinmak.back.order.out.kafka;

import com.jjinmak.back.order.domain.OrderOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class OrderOutBoxSender {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void send(OrderOutbox outbox){
        kafkaTemplate.send(outbox.getTopic(), outbox.getMessageKey(), objectMapper.readTree(outbox.getPayload()))
                .orTimeout(10, TimeUnit.SECONDS)
                .join();
    }
}
