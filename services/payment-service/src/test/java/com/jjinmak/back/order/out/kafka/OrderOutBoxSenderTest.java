package com.jjinmak.back.order.out.kafka;

import com.jjinmak.back.order.domain.OrderOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
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
class OrderOutBoxSenderTest {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    ObjectMapper objectMapper = JsonMapper.builder().build();

    OrderOutBoxSender orderOutBoxSender;

    OrderOutbox outbox = new OrderOutbox(
            "order.send-product", "1",
            "{\"orderId\":1,\"winnerId\":2,\"sellerId\":3,\"carrier\":\"CJ\",\"waybill\":\"1234567890\"}"
    );

    @BeforeEach
    void setUp() {
        orderOutBoxSender = new OrderOutBoxSender(kafkaTemplate, objectMapper);
    }

    @Test
    @DisplayName("저장된 JSON을 JsonNode로 바꿔 아웃박스의 토픽과 키로 보낸다")
    void send_sendsPayloadAsJsonNode() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        orderOutBoxSender.send(outbox);

        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("order.send-product"), eq("1"), value.capture());
        assertThat(value.getValue()).isInstanceOf(JsonNode.class);
        assertThat(value.getValue()).isEqualTo(objectMapper.readTree(outbox.getPayload()));
    }

    @Test
    @DisplayName("Kafka 응답을 기다리지 않고, 아직 끝나지 않은 future를 바로 돌려준다")
    void send_returnsWithoutWaiting() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());

        CompletableFuture<SendResult<String, Object>> future = orderOutBoxSender.send(outbox);

        assertThat(future).isNotDone();
    }

    @Test
    @DisplayName("Kafka 응답이 실패로 끝나면 실패로 끝나는 future를 돌려준다")
    void send_returnsFailedFutureWhenSendFails() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        CompletableFuture<SendResult<String, Object>> future = orderOutBoxSender.send(outbox);

        assertThatThrownBy(future::join)
                .isInstanceOf(CompletionException.class)
                .hasRootCauseMessage("broker down");
    }

    @Test
    @DisplayName("Kafka 전송 호출이 바로 예외를 던지면 그 예외를 그대로 던진다")
    void send_rethrowsImmediateFailure() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(new KafkaException("Send failed"));

        assertThatThrownBy(() -> orderOutBoxSender.send(outbox))
                .isInstanceOf(KafkaException.class)
                .hasMessage("Send failed");
    }
}
