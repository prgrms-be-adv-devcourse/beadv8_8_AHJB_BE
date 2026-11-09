package com.jjinmak.delivery.out;

import com.jjinmak.contracts.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.contracts.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.delivery.out.eventPublisher.EventPublisher;
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

import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.DELIVERED;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.IN_TRANSIT;
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
                new Published(new DeliveryCreatedEvent(1L), "delivery.created", "1"),
                new Published(new DeliveryStatusUpdatedEvent(1L, 2L, 3L, IN_TRANSIT, DELIVERED, at), "delivery.status-updated", "1")
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
