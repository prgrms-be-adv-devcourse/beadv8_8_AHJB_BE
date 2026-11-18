package com.jjinmak.boundedcontext.delivery.out.kafka;

import com.jjinmak.boundedcontext.delivery.domain.DeliveryOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryOutboxSenderTest {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    ObjectMapper objectMapper = JsonMapper.builder().build();

    DeliveryOutboxSender deliveryOutboxSender;

    DeliveryOutbox outbox = new DeliveryOutbox(
            "delivery.status-updated", "1",
            "{\"orderId\":1,\"winnerId\":2,\"sellerId\":3,\"beforeStatus\":\"IN_TRANSIT\",\"afterStatus\":\"DELIVERED\",\"updatedAt\":\"2026-10-30T12:00:00\"}"
    );

    @BeforeEach
    void setUp() {
        deliveryOutboxSender = new DeliveryOutboxSender(kafkaTemplate, objectMapper);
    }

    @Test
    @DisplayName("저장된 JSON을 JsonNode로 바꿔 아웃박스의 토픽과 키로 보낸다")
    void send_sendsPayloadAsJsonNode() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        deliveryOutboxSender.send(outbox);

        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("delivery.status-updated"), eq("1"), value.capture());
        assertThat(value.getValue()).isInstanceOf(JsonNode.class);
        assertThat(value.getValue()).isEqualTo(objectMapper.readTree(outbox.getPayload()));
    }

    @Test
    @DisplayName("Kafka 전송이 실패하면 예외를 던진다")
    void send_throwsWhenSendFails() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThatThrownBy(() -> deliveryOutboxSender.send(outbox))
                .isInstanceOf(CompletionException.class)
                .hasRootCauseMessage("broker down");
    }

    @Test
    @DisplayName("Kafka가 응답하지 않으면 10초까지 기다린 뒤 예외를 던진다")
    void send_throwsAfter10SecondsWithoutAck() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
        long started = System.nanoTime();

        assertTimeoutPreemptively(Duration.ofSeconds(15), () ->
                assertThatThrownBy(() -> deliveryOutboxSender.send(outbox))
                        .isInstanceOf(CompletionException.class)
                        .hasRootCauseInstanceOf(TimeoutException.class));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(Duration.ofSeconds(10));
    }
}
