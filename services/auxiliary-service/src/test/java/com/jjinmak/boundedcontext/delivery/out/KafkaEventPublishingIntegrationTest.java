package com.jjinmak.boundedcontext.delivery.out;

import com.jjinmak.boundedcontext.delivery.app.DeliveryFacade;
import com.jjinmak.boundedcontext.delivery.domain.DeliveryOutbox;
import com.jjinmak.boundedcontext.delivery.in.DeliveryScheduler;
import com.jjinmak.boundedcontext.delivery.out.eventPublisher.EventPublisher;
import com.jjinmak.boundedcontext.delivery.out.repository.DeliveryOutboxRepository;
import com.jjinmak.contracts.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.contracts.delivery.event.DeliveryStatusUpdatedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serializer;
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
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.DELIVERED;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.IN_TRANSIT;
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
    DeliveryScheduler scheduledDeliveryScheduler;

    @Autowired
    EventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    DeliveryFacade deliveryFacade;

    @Autowired
    DeliveryOutboxRepository deliveryOutboxRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ProducerFactory<Object, Object> producerFactory;

    DeliveryScheduler deliveryScheduler;

    @BeforeEach
    void setUp() {
        deliveryScheduler = new DeliveryScheduler(deliveryFacade);
    }

    @AfterEach
    void tearDown() {
        deliveryOutboxRepository.deleteAllInBatch();
    }

    static Stream<Published> events() {
        LocalDateTime at = LocalDateTime.of(2026, 10, 30, 12, 0, 0, 123_456_789);
        return Stream.of(
                new Published(new DeliveryCreatedEvent(1L), "delivery.created", "1"),
                new Published(new DeliveryStatusUpdatedEvent(1L, 2L, 3L, IN_TRANSIT, DELIVERED, at), "delivery.status-updated", "1")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    @DisplayName("이벤트를 발행한 트랜잭션이 커밋되면 토픽, 키, 이벤트 JSON이 발행 전 상태로 아웃박스에 남는다")
    void committed_savesOutbox(Published published) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(published.event()));

        List<DeliveryOutbox> saved = deliveryOutboxRepository.findAll();
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

        assertThat(deliveryOutboxRepository.count()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    @DisplayName("이벤트를 발행한 뒤 커밋 직전에 실패해 롤백되면 아웃박스에 남지 않는다")
    void failedBeforeCommit_doesNotSaveOutbox(Published published) {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(published.event());
            TransactionSynchronizationManager.registerSynchronization(new FailBeforeCommit());
        })).isInstanceOf(IllegalStateException.class);

        assertThat(deliveryOutboxRepository.count()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    @DisplayName("스케줄러는 아웃박스의 토픽과 키로 저장된 JSON을 그대로 보내고, 보낸 뒤 발행 시각을 기록한다")
    void relay_sendsStoredJsonAndMarksPublished(Published published) {
        doReturn(CompletableFuture.completedFuture(null)).when(kafkaTemplate).send(anyString(), anyString(), any());
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(published.event()));

        deliveryScheduler.sendOutbox();

        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(published.topic()), eq(published.key()), value.capture());
        assertThat(value.getValue()).isInstanceOf(JsonNode.class);
        assertThat(objectMapper.treeToValue((JsonNode) value.getValue(), published.event().getClass())).isEqualTo(published.event());
        assertThat(deliveryOutboxRepository.findAll().getFirst().getPublishedAt()).isNotNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    @DisplayName("스케줄러가 보낸 값은 Kafka 직렬화 결과가 이벤트를 바로 보낼 때와 같은 바이트이고, 타입 헤더가 붙지 않는다")
    void relay_serializesToSameBytesAsEvent(Published published) throws Exception {
        doReturn(CompletableFuture.completedFuture(null)).when(kafkaTemplate).send(anyString(), anyString(), any());
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(published.event()));

        deliveryScheduler.sendOutbox();

        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(published.topic()), eq(published.key()), value.capture());
        Serializer<Object> serializer = producerValueSerializer();
        RecordHeaders headers = new RecordHeaders();
        assertThat(serializer.serialize(published.topic(), headers, value.getValue()))
                .isEqualTo(serializer.serialize(published.topic(), new RecordHeaders(), published.event()));
        assertThat(headers.toArray()).isEmpty();
    }

    @Test
    @DisplayName("Kafka 전송이 실패하면 그 아웃박스는 발행으로 표시하지 않고, 그 회차에는 다음 아웃박스를 보내지 않는다")
    void relay_sendFails_keepsUnpublishedAndStops() {
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")))
                .when(kafkaTemplate).send(anyString(), anyString(), any());
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(new DeliveryCreatedEvent(1L));
            eventPublisher.publish(new DeliveryCreatedEvent(2L));
        });

        deliveryScheduler.sendOutbox();

        verify(kafkaTemplate).send(eq("delivery.created"), eq("1"), any());
        verify(kafkaTemplate, never()).send(eq("delivery.created"), eq("2"), any());
        assertThat(deliveryOutboxRepository.findAll()).allSatisfy(outbox -> assertThat(outbox.getPublishedAt()).isNull());
    }

    @SuppressWarnings("unchecked")
    private Serializer<Object> producerValueSerializer() throws Exception {
        Map<String, Object> config = producerFactory.getConfigurationProperties();
        Object type = config.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG);
        Class<?> serializerClass = type instanceof Class<?> c ? c : Class.forName((String) type);
        Serializer<Object> serializer = (Serializer<Object>) serializerClass.getDeclaredConstructor().newInstance();
        serializer.configure(config, false);
        return serializer;
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
