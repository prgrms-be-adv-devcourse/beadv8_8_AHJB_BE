package com.jjinmak.back.payment.out.kafka;

import com.jjinmak.back.payment.domain.PaymentOutbox;
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxSenderTest {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    ObjectMapper objectMapper = JsonMapper.builder().build();

    PaymentOutboxSender paymentOutboxSender;

    PaymentOutbox outbox = new PaymentOutbox(
            "payment.payment-refund", "1001",
            "{\"refundId\":9,\"orderId\":1001,\"paymentId\":7,\"amount\":33000,\"refundedAt\":\"2026-11-19T12:00:00\"}"
    );

    @BeforeEach
    void setUp() {
        paymentOutboxSender = new PaymentOutboxSender(kafkaTemplate, objectMapper);
    }

    @Test
    @DisplayName("저장된 JSON을 JsonNode로 바꿔 아웃박스의 토픽과 키로 보낸다")
    void send_sendsPayloadAsJsonNode() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        paymentOutboxSender.send(outbox);

        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("payment.payment-refund"), eq("1001"), value.capture());
        assertThat(value.getValue()).isInstanceOf(JsonNode.class);
        assertThat(value.getValue()).isEqualTo(objectMapper.readTree(outbox.getPayload()));
    }

    @Test
    @DisplayName("Kafka 전송이 실패하면 예외를 던진다")
    void send_throwsWhenSendFails() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThatThrownBy(() -> paymentOutboxSender.send(outbox))
                .isInstanceOf(CompletionException.class)
                .hasRootCauseMessage("broker down");
    }
}
