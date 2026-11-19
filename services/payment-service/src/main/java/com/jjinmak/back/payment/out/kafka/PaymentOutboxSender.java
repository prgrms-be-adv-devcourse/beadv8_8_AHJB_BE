package com.jjinmak.back.payment.out.kafka;

import com.jjinmak.back.payment.domain.PaymentOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;


@Component
@RequiredArgsConstructor
public class PaymentOutboxSender {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void send(PaymentOutbox outbox) {
        kafkaTemplate.send(outbox.getTopic(), outbox.getMessageKey(), objectMapper.readTree(outbox.getPayload()))
                .orTimeout(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .join();
    }
}
