package com.jjinmak.back.order.out.kafka;

import com.jjinmak.back.order.domain.OrderOutbox;
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
    @DisplayName("Kafka 전송이 실패하면 예외를 던진다")
    void send_throwsWhenSendFails() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThatThrownBy(() -> orderOutBoxSender.send(outbox))
                .isInstanceOf(CompletionException.class)
                .hasRootCauseMessage("broker down");
    }
}
