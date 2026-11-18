package com.jjinmak.boundedcontext.delivery.app;

import com.jjinmak.boundedcontext.delivery.domain.DeliveryOutbox;
import com.jjinmak.boundedcontext.delivery.out.kafka.DeliveryOutboxSender;
import com.jjinmak.boundedcontext.delivery.out.repository.DeliveryOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryOutboxUseCaseTest {

    @Mock
    DeliveryOutboxRepository deliveryOutboxRepository;

    @Mock
    DeliveryOutboxSender deliveryOutboxSender;

    @InjectMocks
    DeliveryOutboxUseCase deliveryOutboxUseCase;

    @Test
    @DisplayName("발행하지 않은 아웃박스는 저장소의 조회 결과를 그대로 돌려준다")
    void findUnpublished_returnsRepositoryResult() {
        List<DeliveryOutbox> unpublished = List.of(new DeliveryOutbox("delivery.created", "1", "{}"));
        when(deliveryOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc()).thenReturn(unpublished);

        assertThat(deliveryOutboxUseCase.findUnpublished()).isSameAs(unpublished);
    }

    @Test
    @DisplayName("아웃박스를 전송기로 보낸다")
    void send_delegatesToSender() {
        DeliveryOutbox outbox = new DeliveryOutbox("delivery.created", "1", "{}");

        deliveryOutboxUseCase.send(outbox);

        verify(deliveryOutboxSender).send(outbox);
    }

    @Test
    @DisplayName("발행으로 표시하면 그 아웃박스에 현재 시각을 발행 시각으로 기록한다")
    void markPublished_setsPublishedAt() {
        DeliveryOutbox outbox = new DeliveryOutbox("delivery.created", "1", "{}");
        when(deliveryOutboxRepository.findById(1L)).thenReturn(Optional.of(outbox));
        LocalDateTime before = LocalDateTime.now();

        deliveryOutboxUseCase.markPublished(1L);

        assertThat(outbox.getPublishedAt()).isBetween(before, LocalDateTime.now());
    }

    @Test
    @DisplayName("발행으로 표시할 아웃박스가 없으면 예외 없이 끝난다")
    void markPublished_missingOutbox_doesNothing() {
        when(deliveryOutboxRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatCode(() -> deliveryOutboxUseCase.markPublished(1L)).doesNotThrowAnyException();
    }
}
