package com.jjinmak.back.order.out.kafka;

import com.jjinmak.contracts.cart.event.CartPaymentDefaultEvent;
import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.contracts.order.event.OrderGroupDeletedEvent;
import com.jjinmak.contracts.order.event.OrderPaymentSucceedEvent;
import com.jjinmak.contracts.order.event.OrderRefundAcceptedEvent;
import com.jjinmak.contracts.order.event.OrderRefundRequestedEvent;
import com.jjinmak.contracts.order.event.OrderRemindAutoConfirmEvent;
import com.jjinmak.contracts.order.event.OrderRemindPaymentEvent;
import com.jjinmak.contracts.order.event.OrderSendProductEvent;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.order.app.OrderFacade;
import com.jjinmak.back.order.domain.OrderOutbox;
import com.jjinmak.back.order.in.OrderScheduler;
import com.jjinmak.back.order.out.repository.OrderOutboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static com.jjinmak.contracts.delivery.domain.CarrierType.CJ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:kafkapublishing;MODE=PostgreSQL")
class KafkaEventPublishingIntegrationTest {

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    OrderScheduler scheduledOrderScheduler;

    @Autowired
    EventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    OrderFacade orderFacade;

    @Autowired
    OrderOutboxRepository orderOutboxRepository;

    @Autowired
    ObjectMapper objectMapper;

    OrderScheduler orderScheduler;

    @BeforeEach
    void setUp() {
        orderScheduler = new OrderScheduler(orderFacade);
    }

    @AfterEach
    void tearDown() {
        orderOutboxRepository.deleteAllInBatch();
    }

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
    @DisplayName("이벤트를 발행한 트랜잭션이 커밋되면 토픽, 키, 이벤트 JSON이 발행 전 상태로 아웃박스에 남는다")
    void committed_savesOutbox(Published published) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(published.event()));

        List<OrderOutbox> saved = orderOutboxRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getTopic()).isEqualTo(published.topic());
        assertThat(saved.getFirst().getMessageKey()).isEqualTo(published.key());
        assertThat(objectMapper.readValue(saved.getFirst().getPayload(), published.event().getClass())).isEqualTo(published.event());
        assertThat(saved.getFirst().getPublishedAt()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    @DisplayName("이벤트를 발행한 트랜잭션이 롤백되면 아웃박스에 남지 않는다")
    void rolledBack_doesNotSaveOutbox(Published published) {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(published.event());
            status.setRollbackOnly();
        });

        assertThat(orderOutboxRepository.count()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    @DisplayName("이벤트를 발행한 뒤 커밋 직전에 실패해 롤백되면 아웃박스에 남지 않는다")
    void failedBeforeCommit_doesNotSaveOutbox(Published published) {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(published.event());
            TransactionSynchronizationManager.registerSynchronization(new FailBeforeCommit());
        })).isInstanceOf(IllegalStateException.class);

        assertThat(orderOutboxRepository.count()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    @DisplayName("스케줄러는 아웃박스의 토픽과 키로 저장된 JSON을 그대로 보내고, 보낸 뒤 발행 시각을 기록한다")
    void relay_sendsStoredJsonAndMarksPublished(Published published) {
        doReturn(CompletableFuture.completedFuture(null)).when(kafkaTemplate).send(anyString(), anyString(), any());
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(published.event()));

        orderScheduler.sendOutbox();

        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(published.topic()), eq(published.key()), value.capture());
        assertThat(value.getValue()).isInstanceOf(JsonNode.class);
        assertThat(objectMapper.treeToValue((JsonNode) value.getValue(), published.event().getClass())).isEqualTo(published.event());
        assertThat(orderOutboxRepository.findAll().getFirst().getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("Kafka 전송이 실패하면 그 아웃박스는 발행으로 표시하지 않고, 그 회차에는 다음 아웃박스를 보내지 않는다")
    void relay_sendFails_keepsUnpublishedAndStops() {
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")))
                .when(kafkaTemplate).send(anyString(), anyString(), any());
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(new OrderSendProductEvent(1L, 2L, 3L, CJ, "1111111111"));
            eventPublisher.publish(new OrderSendProductEvent(2L, 2L, 3L, CJ, "2222222222"));
        });

        orderScheduler.sendOutbox();

        verify(kafkaTemplate).send(eq("order.send-product"), eq("1"), any());
        verify(kafkaTemplate, never()).send(eq("order.send-product"), eq("2"), any());
        assertThat(orderOutboxRepository.findAll()).allSatisfy(outbox -> assertThat(outbox.getPublishedAt()).isNull());
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
