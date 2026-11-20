package com.jjinmak.back.refund.out;

import com.jjinmak.back.refund.domain.RefundOutbox;
import com.jjinmak.contracts.refund.event.RefundAcceptedEvent;
import com.jjinmak.contracts.refund.event.RefundCompletedEvent;
import com.jjinmak.contracts.refund.event.RefundRejectedEvent;
import com.jjinmak.contracts.refund.event.RefundRetryRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import static com.jjinmak.contracts.refund.event.RefundEventTopics.*;

/**
 * 환불 Spring 이벤트를 Kafka 로 바로 보내지 않고, 같은 트랜잭션(BEFORE_COMMIT)에서 refund_outbox 에 저장한다.
 * 전송은 RefundRetryScheduler.sendOutbox 가 1초마다 한다. 토픽·키는 예전 RefundKafkaEventPublisher 와 같다.
 */
@Component
@RequiredArgsConstructor
public class RefundOutboxWriter {

    private final RefundOutboxRepository refundOutboxRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(RefundAcceptedEvent event) {
        save(REFUND_ACCEPTED, event.refund().refundId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(RefundRejectedEvent event) {
        save(REFUND_REJECTED, event.refund().refundId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(RefundRetryRequestedEvent event) {
        save(REFUND_RETRY_REQUESTED, event.refund().refundId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(RefundCompletedEvent event) {
        save(REFUND_COMPLETED, event.refund().refundId(), event);
    }

    private void save(String topic, Long key, Object event) {
        refundOutboxRepository.save(new RefundOutbox(topic, String.valueOf(key), objectMapper.writeValueAsString(event)));
    }
}