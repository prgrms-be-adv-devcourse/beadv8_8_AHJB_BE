package com.jjinmak.back.boundedContext.settlement.out;

import com.jjinmak.back.shared.settlement.event.SettlementRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.jjinmak.back.shared.settlement.event.SettlementEventTopics.*;

@Component
@RequiredArgsConstructor
public class SettlementKafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(SettlementRequestedEvent event) {
        kafkaTemplate.send(SETTLEMENT_REQUESTED, String.valueOf(event.settlement().settlementId()), event);
    }
}