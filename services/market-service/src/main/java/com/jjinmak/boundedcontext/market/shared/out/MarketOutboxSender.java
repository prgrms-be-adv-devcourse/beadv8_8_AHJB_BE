package com.jjinmak.boundedcontext.market.shared.out;

import com.jjinmak.boundedcontext.market.shared.domain.MarketOutbox;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 아웃박스 묶음을 Kafka로 보내고 브로커 응답(ack)을 확인한다 (#548).
 *
 * <p>팀 공통 전송기는 1건 보내고 ack를 기다린 뒤 다음 건을 보낸다. 경매는 같은 분 종료처럼 1,000건이 한꺼번에 쌓여서,
 * 그 방식으로는 마지막 낙찰 이벤트가 약 20초 늦게 나갔다(2차 측정). 그래서 묶음 전체를 먼저 보내고(send는 비동기),
 * 앞에서부터 ack를 확인한다. 실패하면 그 앞까지만 성공으로 돌려준다 → 같은 경매 이벤트의 순서가 뒤집히지 않는다.
 * 본문은 팀 공통과 같이 JsonNode로 보낸다(문자열을 그대로 보내면 한 번 더 직렬화됨).
 */
@Slf4j
@Component
public class MarketOutboxSender {

    private static final long ACK_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Timer sendTimer;

    public MarketOutboxSender(KafkaTemplate<String, Object> kafkaTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        // 묶음 1개를 보내고 ack를 모두 확인하기까지 걸린 시간 (실패 포함)
        this.sendTimer = Timer.builder("market.outbox.send")
                .description("아웃박스 묶음 Kafka 전송(ack까지) 시간")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    /**
     * @return 앞에서부터 연속으로 전송에 성공한 건수. 묶음 크기보다 작으면 그 다음 행에서 실패했다
     */
    public int sendAll(List<MarketOutbox> outboxes) {
        return sendTimer.record(() -> {
            List<CompletableFuture<?>> futures = new ArrayList<>(outboxes.size());
            for (MarketOutbox outbox : outboxes) {
                try {
                    futures.add(kafkaTemplate.send(outbox.getTopic(), outbox.getMessageKey(), objectMapper.readTree(outbox.getPayload())));
                } catch (RuntimeException e) {
                    // 브로커 중단 시 send가 메타데이터를 기다리다(max.block.ms) 예외를 던진다
                    log.error("Market 이벤트 발행 실패, 다음 주기에 다시 보냅니다.\noutboxId: {}\ntopic: {}", outbox.getId(), outbox.getTopic(), e);
                    break;
                }
            }
            int sent = 0;
            for (CompletableFuture<?> future : futures) {
                try {
                    future.orTimeout(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
                } catch (RuntimeException e) {
                    MarketOutbox failed = outboxes.get(sent);
                    log.error("Market 이벤트 발행 실패, 다음 주기에 다시 보냅니다.\noutboxId: {}\ntopic: {}", failed.getId(), failed.getTopic(), e);
                    break;
                }
                sent++;
            }
            return sent;
        });
    }
}
