package com.jjinmak.order.out.kafka;

import com.jjinmak.contracts.cart.event.CartPaymentDefaultEvent;
import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.contracts.order.event.OrderGroupDeletedEvent;
import com.jjinmak.contracts.order.event.OrderPaymentSucceedEvent;
import com.jjinmak.contracts.order.event.OrderRefundAcceptedEvent;
import com.jjinmak.contracts.order.event.OrderRefundRequestedEvent;
import com.jjinmak.contracts.order.event.OrderRemindAutoConfirmEvent;
import com.jjinmak.contracts.order.event.OrderRemindPaymentEvent;
import com.jjinmak.contracts.order.event.OrderSendProductEvent;
import com.jjinmak.order.out.eventPublisher.EventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static com.jjinmak.contracts.delivery.domain.CarrierType.CJ;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:kafkapublishing;MODE=PostgreSQL")
class KafkaEventPublishingIntegrationTest {

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    EventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    static Stream<Published> events() {
        LocalDateTime at = LocalDateTime.of(2026, 10, 30, 12, 0);
        return Stream.of(
                new Published(new OrderConfirmPurchaseEvent(1L, 2L, 3L, 4L, 5L, 30_000L, 3_000L, at), "order.confirm-purchase", "1"),
                new Published(OrderRefundRequestedEvent.byWinner(1L, 2L, 3L, 5L, 30_000L, 3_000L), "order.refund-requested", "1"),
                new Published(new OrderGroupDeletedEvent(7L, 33_000L), "order.group-deleted", "7"),
                new Published(new OrderPaymentSucceedEvent(1L, 4L, 5L, 2L, 3L, at), "order.payment-succeed", "1"),
                new Published(new OrderRefundAcceptedEvent(1L, 4L), "order.refund-accepted", "1"),
                new Published(new OrderSendProductEvent(1L, 2L, 3L, CJ, "1234567890"), "order.send-product", "1"),
                new Published(new CartPaymentDefaultEvent(4L, 5L, 2L, 3L, 30_000L, 3_000L), "order.cart-payment-default", "4"),
                new Published(new OrderRemindPaymentEvent(8L, 2L, 30_000L, 3_000L, at), "order.remind-payment-default", "8"),
                new Published(new OrderRemindAutoConfirmEvent(1L, 2L, at), "order.remind-auto-confirm", "1")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    @DisplayName("이벤트를 발행한 트랜잭션이 커밋되면 Kafka로 보낸다")
    void committed_sends(Published published) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(published.event()));

        verify(kafkaTemplate).send(published.topic(), published.key(), published.event());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    @DisplayName("이벤트를 발행한 트랜잭션이 롤백되면 Kafka로 보내지 않는다")
    void rolledBack_doesNotSend(Published published) {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(published.event());
            status.setRollbackOnly();
        });

        verify(kafkaTemplate, never()).send(anyString(), anyString(), eq(published.event()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    @DisplayName("이벤트를 발행한 뒤 커밋 직전에 실패해 롤백되면 Kafka로 보내지 않는다")
    void failedBeforeCommit_doesNotSend(Published published) {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(published.event());
            TransactionSynchronizationManager.registerSynchronization(new FailBeforeCommit());
        })).isInstanceOf(IllegalStateException.class);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), eq(published.event()));
    }

    record Published(Object event, String topic, String key) {

        @Override
        public String toString() {
            return topic;
        }
    }

    static class FailBeforeCommit implements TransactionSynchronization {

        @Override
        public void beforeCommit(boolean readOnly) {
            throw new IllegalStateException("커밋 직전 실패");
        }
    }
}
