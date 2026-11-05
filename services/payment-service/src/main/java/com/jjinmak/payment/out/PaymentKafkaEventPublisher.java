package com.jjinmak.payment.out;

import com.jjinmak.contracts.payment.event.PaymentEventTopics;
import com.jjinmak.contracts.payment.event.PaymentPaidEvent;
import com.jjinmak.contracts.payment.event.WalletRefundedEvent;
import com.jjinmak.contracts.payment.event.WalletReleasedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaymentKafkaEventPublisher {

    private final KafkaTemplate<String, Object>  kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(PaymentPaidEvent event) {
        kafkaTemplate.send(PaymentEventTopics.PAYMENT_PAID_EVENT, String.valueOf(event.orderGroupId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(WalletRefundedEvent event) {
        kafkaTemplate.send(PaymentEventTopics.PAYMENT_REFUND_EVENT, String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(WalletReleasedEvent event) {
        kafkaTemplate.send(PaymentEventTopics.PAYMENT_RELEASED_EVENT, String.valueOf(event.orderId()), event);
    }
}
