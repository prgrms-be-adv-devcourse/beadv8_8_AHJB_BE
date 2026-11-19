package com.jjinmak.back.settlement.out;

import com.jjinmak.back.settlement.domain.SettlementOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

/** 아웃박스 한 행을 Kafka 로 보낸다. 브로커 응답을 기다려 실패하면 예외를 던진다 (호출자가 published 표시를 건너뛴다) */
@Component
@RequiredArgsConstructor
public class SettlementOutboxSender {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void send(SettlementOutbox outbox) {
        kafkaTemplate.send(outbox.getTopic(), outbox.getMessageKey(), objectMapper.readTree(outbox.getPayload()))
                .orTimeout(10, TimeUnit.SECONDS)
                .join();
    }
}