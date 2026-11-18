package com.jjinmak.back.boundedContext.refund.out;

import com.jjinmak.back.shared.refund.event.RefundAcceptedEvent;
import com.jjinmak.back.shared.refund.event.RefundCompletedEvent;
import com.jjinmak.back.shared.refund.event.RefundRejectedEvent;
import com.jjinmak.back.shared.refund.event.RefundRetryRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.jjinmak.back.shared.refund.event.RefundEventTopics.*;

@Component
@RequiredArgsConstructor
public class RefundKafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(RefundAcceptedEvent event) {
        kafkaTemplate.send(REFUND_ACCEPTED, String.valueOf(event.refund().refundId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(RefundRejectedEvent event) {
        kafkaTemplate.send(REFUND_REJECTED, String.valueOf(event.refund().refundId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(RefundRetryRequestedEvent event) {
        kafkaTemplate.send(REFUND_RETRY_REQUESTED, String.valueOf(event.refund().refundId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(RefundCompletedEvent event) {
        kafkaTemplate.send(REFUND_COMPLETED, String.valueOf(event.refund().refundId()), event);
    }
}
