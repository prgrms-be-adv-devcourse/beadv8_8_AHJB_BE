package com.jjinmak.boundedcontext.market.shared.out;

import com.jjinmak.boundedcontext.market.shared.domain.MarketOutbox;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketOutboxSenderTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private SimpleMeterRegistry meterRegistry;
    private MarketOutboxSender sender;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        sender = new MarketOutboxSender(kafkaTemplate, objectMapper, meterRegistry);
    }

    private MarketOutbox 유찰(long auctionId) {
        return new MarketOutbox("auction.failed", String.valueOf(auctionId),
                "{\"auctionId\":" + auctionId + ",\"productId\":2,\"reason\":\"NO_BID\",\"failedAt\":\"2026-11-20T12:00:00\",\"sellerId\":3}");
    }

    @Test
    @DisplayName("묶음 전체를 저장 순서대로 JsonNode로 보내고, 모두 성공하면 전체 건수를 돌려준다")
    void sendAll_sendsAllAsJsonNode() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        MarketOutbox first = 유찰(1L);
        MarketOutbox second = 유찰(2L);

        int sent = sender.sendAll(List.of(first, second));

        assertThat(sent).isEqualTo(2);
        InOrder order = inOrder(kafkaTemplate);
        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        order.verify(kafkaTemplate).send(eq("auction.failed"), eq("1"), value.capture());
        order.verify(kafkaTemplate).send(eq("auction.failed"), eq("2"), any());
        assertThat(value.getValue()).isInstanceOf(JsonNode.class);
        assertThat(value.getValue()).isEqualTo(objectMapper.readTree(first.getPayload()));
        assertThat(meterRegistry.get("market.outbox.send").timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("중간 행의 ack가 실패하면 그 앞까지만 성공으로 센다 (순서 유지)")
    void sendAll_countsOnlyLeadingSuccesses() {
        when(kafkaTemplate.send(anyString(), eq("1"), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaTemplate.send(anyString(), eq("2"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        when(kafkaTemplate.send(anyString(), eq("3"), any())).thenReturn(CompletableFuture.completedFuture(null));

        int sent = sender.sendAll(List.of(유찰(1L), 유찰(2L), 유찰(3L)));

        assertThat(sent).isEqualTo(1);
    }

    @Test
    @DisplayName("send가 바로 예외를 던지면 그 뒤 행은 보내지 않는다")
    void sendAll_stopsWhenSendThrows() {
        when(kafkaTemplate.send(anyString(), eq("1"), any())).thenThrow(new IllegalStateException("metadata timeout"));

        int sent = sender.sendAll(List.of(유찰(1L), 유찰(2L)));

        assertThat(sent).isZero();
        verify(kafkaTemplate, never()).send(anyString(), eq("2"), any());
    }
}
