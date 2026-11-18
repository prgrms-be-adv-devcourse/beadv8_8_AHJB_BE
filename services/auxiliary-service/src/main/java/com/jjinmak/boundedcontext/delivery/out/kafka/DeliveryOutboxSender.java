package com.jjinmak.boundedcontext.delivery.out.kafka;

import com.jjinmak.boundedcontext.delivery.domain.DeliveryOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class DeliveryOutboxSender {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void send(DeliveryOutbox outbox){
        kafkaTemplate.send(outbox.getTopic(), outbox.getMessageKey(), objectMapper.readTree(outbox.getPayload()))
                .orTimeout(10, TimeUnit.SECONDS)
                .join();
    }
}
