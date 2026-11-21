package com.jjinmak.back.order.app;

import com.jjinmak.back.order.app.dto.OutboxSendResult;
import com.jjinmak.back.order.domain.OrderOutbox;
import com.jjinmak.back.order.out.kafka.OrderOutBoxSender;
import com.jjinmak.back.order.out.repository.OrderOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderOutboxUseCaseTest {

    @Mock
    OrderOutboxRepository orderOutboxRepository;

    @Mock
    OrderOutBoxSender orderOutBoxSender;

    @InjectMocks
    OrderOutboxUseCase orderOutboxUseCase;

    @Test
    @DisplayName("묶음의 아웃박스를 모두 보낸 뒤 응답을 기다려, 보낸 id를 순서대로 돌려준다")
    void sendAll_returnsSentIds() {
        OrderOutbox first = outbox(1L);
        OrderOutbox second = outbox(2L);
        OrderOutbox third = outbox(3L);
        when(orderOutBoxSender.send(first)).thenReturn(CompletableFuture.completedFuture(null));
        when(orderOutBoxSender.send(second)).thenReturn(CompletableFuture.completedFuture(null));
        when(orderOutBoxSender.send(third)).thenReturn(CompletableFuture.completedFuture(null));

        OutboxSendResult result = orderOutboxUseCase.sendAll(List.of(first, second, third));

        assertThat(result.sentIds()).containsExactly(1L, 2L, 3L);
        assertThat(result.failures()).isEmpty();
    }

    @Test
    @DisplayName("응답이 실패로 끝난 아웃박스는 실패로 돌려주고 보낸 id에서 빼며, 나머지는 그대로 보낸다")
    void sendAll_separatesFailedResponse() {
        OrderOutbox first = outbox(1L);
        OrderOutbox second = outbox(2L);
        OrderOutbox third = outbox(3L);
        IllegalStateException cause = new IllegalStateException("broker down");
        when(orderOutBoxSender.send(first)).thenReturn(CompletableFuture.completedFuture(null));
        when(orderOutBoxSender.send(second)).thenReturn(CompletableFuture.failedFuture(cause));
        when(orderOutBoxSender.send(third)).thenReturn(CompletableFuture.completedFuture(null));

        OutboxSendResult result = orderOutboxUseCase.sendAll(List.of(first, second, third));

        assertThat(result.sentIds()).containsExactly(1L, 3L);
        assertThat(result.failures()).containsOnlyKeys(second);
        assertThat(result.failures().get(second)).isSameAs(cause);
    }

    @Test
    @DisplayName("보내는 순간 예외가 나면 그 아웃박스를 실패로 돌려주고, 묶음의 나머지는 보내지 않는다")
    void sendAll_stopsAtImmediateFailure() {
        OrderOutbox first = outbox(1L);
        OrderOutbox second = outbox(2L);
        OrderOutbox third = outbox(3L);
        KafkaException cause = new KafkaException("Send failed");
        when(orderOutBoxSender.send(first)).thenReturn(CompletableFuture.completedFuture(null));
        when(orderOutBoxSender.send(second)).thenThrow(cause);

        OutboxSendResult result = orderOutboxUseCase.sendAll(List.of(first, second, third));

        assertThat(result.sentIds()).containsExactly(1L);
        assertThat(result.failures()).containsOnlyKeys(second);
        assertThat(result.failures().get(second)).isSameAs(cause);
        verify(orderOutBoxSender, never()).send(third);
    }

    @Test
    @DisplayName("보낸 id들을 현재 시각으로 한 번에 발행 표시한다")
    void markPublished_marksIdsAtOnce() {
        LocalDateTime before = LocalDateTime.now();

        orderOutboxUseCase.markPublished(List.of(1L, 3L));

        ArgumentCaptor<LocalDateTime> publishedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderOutboxRepository).markPublished(eq(List.of(1L, 3L)), publishedAt.capture());
        assertThat(publishedAt.getValue()).isBetween(before, LocalDateTime.now());
    }

    @Test
    @DisplayName("보낸 id가 없으면 발행 표시 쿼리를 실행하지 않는다")
    void markPublished_skipsEmptyIds() {
        orderOutboxUseCase.markPublished(List.of());

        verify(orderOutboxRepository, never()).markPublished(anyList(), any());
    }

    private OrderOutbox outbox(Long id) {
        OrderOutbox outbox = new OrderOutbox("order.send-product", String.valueOf(id), "{}");
        ReflectionTestUtils.setField(outbox, "id", id);
        return outbox;
    }
}
