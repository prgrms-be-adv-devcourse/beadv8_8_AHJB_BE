package com.jjinmak.back.payment.out.outbox;

import com.jjinmak.back.payment.domain.PaymentOutbox;
import com.jjinmak.back.payment.out.repository.PaymentOutboxRepository;
import com.jjinmak.contracts.payment.event.PaymentPaidEvent;
import com.jjinmak.contracts.payment.event.WalletRefundedEvent;
import com.jjinmak.contracts.payment.event.WalletReleasedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import static com.jjinmak.contracts.payment.event.PaymentEventTopics.PAYMENT_PAID_EVENT;
import static com.jjinmak.contracts.payment.event.PaymentEventTopics.PAYMENT_REFUND_EVENT;
import static com.jjinmak.contracts.payment.event.PaymentEventTopics.PAYMENT_RELEASED_EVENT;

/**
 * 결제 이벤트를 커밋 직전에 아웃박스에 저장한다.
 *
 * 이벤트를 낸 트랜잭션 안에서 저장하므로 결제 변경과 이벤트가 함께 커밋되거나 함께 롤백된다.
 * 저장이 실패하면 그 트랜잭션 전체가 롤백된다.
 * 트랜잭션 밖에서 낸 이벤트는 받지 않는다 (결제 이벤트는 모두 파사드의 쓰기 트랜잭션 안에서 발행된다).
 */
@Component
@RequiredArgsConstructor
public class PaymentOutboxWriter {

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(PaymentPaidEvent event) {
        save(PAYMENT_PAID_EVENT, event.orderGroupId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(WalletRefundedEvent event) {
        save(PAYMENT_REFUND_EVENT, event.orderId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(WalletReleasedEvent event) {
        save(PAYMENT_RELEASED_EVENT, event.orderId(), event);
    }

    private void save(String topic, Long key, Object event) {
        paymentOutboxRepository.save(new PaymentOutbox(topic, String.valueOf(key), objectMapper.writeValueAsString(event)));
    }
}
