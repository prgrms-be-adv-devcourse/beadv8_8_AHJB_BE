package com.jjinmak.back.boundedContext.payment.out;

import com.jjinmak.back.shared.payment.event.PaymentPaidEvent;
import com.jjinmak.back.shared.payment.event.WalletRefundedEvent;
import com.jjinmak.back.shared.payment.event.WalletReleasedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;

/** 결제 이벤트가 어느 토픽에 어떤 키로 나가는지. 브로커 없이 KafkaTemplate 호출만 확인한다 */
@ExtendWith(MockitoExtension.class)
class PaymentKafkaEventPublisherTest {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    PaymentKafkaEventPublisher publisher;

    LocalDateTime at = LocalDateTime.of(2026, 11, 2, 12, 0);

    @Test
    @DisplayName("결제 완료 이벤트는 payment.payment-paid 토픽에 주문 그룹 ID를 키로 보낸다")
    void paid_sendsWithOrderGroupIdKey() {
        PaymentPaidEvent event = new PaymentPaidEvent("evt-1", at, 7L, 11L, 2L, 30_000L, at);

        publisher.publish(event);

        verify(kafkaTemplate).send("payment.payment-paid", "7", event);
    }

    @Test
    @DisplayName("환불 완료 이벤트는 payment.payment-refund 토픽에 주문 ID를 키로 보낸다")
    void refunded_sendsWithOrderIdKey() {
        WalletRefundedEvent event = new WalletRefundedEvent(501L, 81L, 11L, 10_000L, at);

        publisher.publish(event);

        verify(kafkaTemplate).send("payment.payment-refund", "81", event);
    }

    @Test
    @DisplayName("정산 릴리즈 이벤트는 payment.payment-released 토픽에 주문 ID를 키로 보낸다")
    void released_sendsWithOrderIdKey() {
        WalletReleasedEvent event = new WalletReleasedEvent(61L, 3L, 10_000L, 1_000L, 9_000L, at);

        publisher.publish(event);

        verify(kafkaTemplate).send("payment.payment-released", "61", event);
    }
}
