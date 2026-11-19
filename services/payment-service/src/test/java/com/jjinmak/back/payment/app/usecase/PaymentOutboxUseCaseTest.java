package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.payment.domain.PaymentOutbox;
import com.jjinmak.back.payment.out.kafka.PaymentOutboxSender;
import com.jjinmak.back.payment.out.repository.PaymentOutboxRepository;
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
class PaymentOutboxUseCaseTest {

    @Mock
    PaymentOutboxRepository paymentOutboxRepository;

    @Mock
    PaymentOutboxSender paymentOutboxSender;

    @InjectMocks
    PaymentOutboxUseCase paymentOutboxUseCase;

    @Test
    @DisplayName("아직 보내지 않은 아웃박스를 저장소에서 순서대로 가져온다")
    void findUnpublished_readsFromRepository() {
        List<PaymentOutbox> unpublished = List.of(
                new PaymentOutbox("payment.payment-paid", "500", "{}"),
                new PaymentOutbox("payment.payment-refund", "1001", "{}"));
        when(paymentOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc()).thenReturn(unpublished);

        assertThat(paymentOutboxUseCase.findUnpublished()).isEqualTo(unpublished);
    }

    @Test
    @DisplayName("아웃박스를 전송기로 보낸다")
    void send_delegatesToSender() {
        PaymentOutbox outbox = new PaymentOutbox("payment.payment-paid", "500", "{}");

        paymentOutboxUseCase.send(outbox);

        verify(paymentOutboxSender).send(outbox);
    }

    @Test
    @DisplayName("발행으로 표시하면 그 아웃박스에 현재 시각을 발행 시각으로 기록한다")
    void markPublished_setsPublishedAt() {
        PaymentOutbox outbox = new PaymentOutbox("payment.payment-paid", "500", "{}");
        when(paymentOutboxRepository.findById(1L)).thenReturn(Optional.of(outbox));
        LocalDateTime before = LocalDateTime.now();

        paymentOutboxUseCase.markPublished(1L);

        assertThat(outbox.getPublishedAt()).isBetween(before, LocalDateTime.now());
    }

    @Test
    @DisplayName("발행으로 표시할 아웃박스가 없으면 아무 것도 하지 않는다")
    void markPublished_missingOutbox_doesNothing() {
        when(paymentOutboxRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatCode(() -> paymentOutboxUseCase.markPublished(1L)).doesNotThrowAnyException();
    }
}
