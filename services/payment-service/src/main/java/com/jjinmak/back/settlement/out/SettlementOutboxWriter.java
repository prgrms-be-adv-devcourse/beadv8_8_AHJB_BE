package com.jjinmak.back.settlement.out;

import com.jjinmak.back.settlement.domain.SettlementOutbox;
import com.jjinmak.contracts.settlement.event.SettlementRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import static com.jjinmak.contracts.settlement.event.SettlementEventTopics.SETTLEMENT_REQUESTED;

/**
 * 정산 Spring 이벤트를 같은 트랜잭션(BEFORE_COMMIT)에서 settlement_outbox 에 저장한다.
 * 전송은 SettlementPayoutScheduler.sendOutbox 가 1초마다 한다. 토픽·키는 예전 SettlementKafkaEventPublisher 와 같다.
 */
@Component
@RequiredArgsConstructor
public class SettlementOutboxWriter {

    private final SettlementOutboxRepository settlementOutboxRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(SettlementRequestedEvent event) {
        save(SETTLEMENT_REQUESTED, event.settlement().settlementId(), event);
    }

    private void save(String topic, Long key, Object event) {
        settlementOutboxRepository.save(new SettlementOutbox(topic, String.valueOf(key), objectMapper.writeValueAsString(event)));
    }
}