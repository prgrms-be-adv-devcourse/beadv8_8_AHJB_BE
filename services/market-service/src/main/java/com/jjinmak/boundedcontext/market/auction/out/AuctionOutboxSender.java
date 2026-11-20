package com.jjinmak.boundedcontext.market.auction.out;

import com.jjinmak.boundedcontext.market.auction.domain.AuctionOutbox;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

// 아웃박스 한 건을 Kafka로 보내고 브로커 응답(ack)까지 기다린다 (#548, 팀 공통 전송기).
// 문자열 그대로 보내면 한 번 더 직렬화되므로 JsonNode로 바꿔 보낸다 → 기존 직접 전송과 같은 본문.
@Component
public class AuctionOutboxSender {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Timer sendTimer;

    public AuctionOutboxSender(KafkaTemplate<String, Object> kafkaTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        // 전송 타이머: send 호출 + ack 대기까지 한 건에 걸린 시간 (실패 포함)
        // 예전 구조에서 스케줄러 스레드가 건마다 쓰던 Kafka 비용이 얼마인지 이 값으로 확인한다
        this.sendTimer = Timer.builder("auction.outbox.send")
                .description("아웃박스 1건 Kafka 전송(ack까지) 시간")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public void send(AuctionOutbox outbox){
        sendTimer.record(() -> {
            kafkaTemplate.send(outbox.getTopic(), outbox.getMessageKey(), objectMapper.readTree(outbox.getPayload()))
                    .orTimeout(10, TimeUnit.SECONDS)
                    .join();
        });
    }
}
