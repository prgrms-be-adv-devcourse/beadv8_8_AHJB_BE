package com.jjinmak.back.order.app;

import com.jjinmak.back.order.domain.OrderOutbox;
import com.jjinmak.back.order.out.kafka.OrderOutBoxSender;
import com.jjinmak.back.order.out.repository.OrderOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("아웃박스를 전송기로 보낸다")
    void send_delegatesToSender() {
        OrderOutbox outbox = new OrderOutbox("order.send-product", "1", "{}");

        orderOutboxUseCase.send(outbox);

        verify(orderOutBoxSender).send(outbox);
    }

    @Test
    @DisplayName("발행으로 표시하면 그 아웃박스에 현재 시각을 발행 시각으로 기록한다")
    void markPublished_setsPublishedAt() {
        OrderOutbox outbox = new OrderOutbox("order.send-product", "1", "{}");
        when(orderOutboxRepository.findById(1L)).thenReturn(Optional.of(outbox));
        LocalDateTime before = LocalDateTime.now();

        orderOutboxUseCase.markPublished(1L);

        assertThat(outbox.getPublishedAt()).isBetween(before, LocalDateTime.now());
    }
}
