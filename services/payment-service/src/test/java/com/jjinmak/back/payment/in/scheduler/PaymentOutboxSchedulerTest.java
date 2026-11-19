package com.jjinmak.back.payment.in.scheduler;

import com.jjinmak.back.payment.app.facade.PaymentOutboxFacade;
import com.jjinmak.back.payment.domain.PaymentOutbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxSchedulerTest {

    @Mock
    PaymentOutboxFacade paymentOutboxFacade;

    @InjectMocks
    PaymentOutboxScheduler paymentOutboxScheduler;

    @Test
    @DisplayName("발행하지 않은 아웃박스를 순서대로 보내고, 한 건씩 보낸 뒤에 발행으로 표시한다")
    void sendOutbox_sendsInOrderAndMarksAfterEachSend() {
        PaymentOutbox first = outbox(1L);
        PaymentOutbox second = outbox(2L);
        when(paymentOutboxFacade.findUnpublished()).thenReturn(List.of(first, second));

        paymentOutboxScheduler.sendOutbox();

        InOrder inOrder = inOrder(paymentOutboxFacade);
        inOrder.verify(paymentOutboxFacade).send(first);
        inOrder.verify(paymentOutboxFacade).markPublished(1L);
        inOrder.verify(paymentOutboxFacade).send(second);
        inOrder.verify(paymentOutboxFacade).markPublished(2L);
    }

    @Test
    @DisplayName("아웃박스 전송이 실패하면 예외를 밖으로 던지지 않고, 그 건은 발행으로 표시하지 않으며 그 회차의 나머지도 보내지 않는다")
    void sendOutbox_stopsAtFirstFailure() {
        PaymentOutbox first = outbox(1L);
        PaymentOutbox second = outbox(2L);
        PaymentOutbox third = outbox(3L);
        when(paymentOutboxFacade.findUnpublished()).thenReturn(List.of(first, second, third));
        // 엄격한 스텁 검사가 first 전송을 "스텁과 다른 인자" 로 막지 않게 한다
        lenient().doThrow(new IllegalStateException("broker down")).when(paymentOutboxFacade).send(second);

        assertThatCode(() -> paymentOutboxScheduler.sendOutbox()).doesNotThrowAnyException();

        verify(paymentOutboxFacade).markPublished(1L);
        verify(paymentOutboxFacade, never()).markPublished(2L);
        verify(paymentOutboxFacade, never()).send(third);
        verify(paymentOutboxFacade, never()).markPublished(3L);
    }

    @Test
    @DisplayName("발행하지 않은 아웃박스가 없으면 아무것도 보내지 않는다")
    void sendOutbox_noTargets() {
        when(paymentOutboxFacade.findUnpublished()).thenReturn(List.of());

        paymentOutboxScheduler.sendOutbox();

        verify(paymentOutboxFacade, never()).send(any());
        verify(paymentOutboxFacade, never()).markPublished(anyLong());
    }

    private PaymentOutbox outbox(Long id) {
        PaymentOutbox outbox = new PaymentOutbox("payment.payment-paid", String.valueOf(id), "{}");
        ReflectionTestUtils.setField(outbox, "id", id);
        return outbox;
    }
}
