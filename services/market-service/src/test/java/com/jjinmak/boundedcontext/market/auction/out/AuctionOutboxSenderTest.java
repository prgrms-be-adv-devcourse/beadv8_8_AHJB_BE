package com.jjinmak.boundedcontext.market.auction.out;

import com.jjinmak.boundedcontext.market.auction.domain.AuctionOutbox;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuctionOutboxSenderTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private SimpleMeterRegistry meterRegistry;
    private AuctionOutboxSender sender;

    private final AuctionOutbox outbox = new AuctionOutbox("auction.failed", "1",
            "{\"auctionId\":1,\"productId\":2,\"reason\":\"NO_BID\",\"failedAt\":\"2026-11-20T12:00:00\",\"sellerId\":3}");

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        sender = new AuctionOutboxSender(kafkaTemplate, objectMapper, meterRegistry);
    }

    @Test
    @DisplayName("저장된 JSON을 JsonNode로 바꿔 아웃박스의 토픽과 키로 보내고 전송 시간을 기록한다")
    void send_sendsPayloadAsJsonNode() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        sender.send(outbox);

        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("auction.failed"), eq("1"), value.capture());
        assertThat(value.getValue()).isInstanceOf(JsonNode.class);
        assertThat(value.getValue()).isEqualTo(objectMapper.readTree(outbox.getPayload()));
        assertThat(meterRegistry.get("auction.outbox.send").timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Kafka 전송이 실패하면 예외를 던진다 (전송 시간은 실패도 기록)")
    void send_throwsWhenSendFails() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThatThrownBy(() -> sender.send(outbox))
                .isInstanceOf(CompletionException.class)
                .hasRootCauseMessage("broker down");
        assertThat(meterRegistry.get("auction.outbox.send").timer().count()).isEqualTo(1);
    }
}
