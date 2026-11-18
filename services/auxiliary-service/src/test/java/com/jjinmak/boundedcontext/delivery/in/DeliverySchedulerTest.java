package com.jjinmak.boundedcontext.delivery.in;

import com.jjinmak.boundedcontext.delivery.app.DeliveryFacade;
import com.jjinmak.boundedcontext.delivery.domain.DeliveryOutbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliverySchedulerTest {

    @Mock
    DeliveryFacade deliveryFacade;

    @InjectMocks
    DeliveryScheduler deliveryScheduler;

    @Test
    @DisplayName("발행하지 않은 아웃박스를 순서대로 보내고, 한 건씩 보낸 뒤에 발행으로 표시한다")
    void sendOutbox_sendsInOrderAndMarksAfterEachSend() {
        DeliveryOutbox first = outbox(1L);
        DeliveryOutbox second = outbox(2L);
        when(deliveryFacade.findUnpublishedOutboxes()).thenReturn(List.of(first, second));

        deliveryScheduler.sendOutbox();

        InOrder inOrder = inOrder(deliveryFacade);
        inOrder.verify(deliveryFacade).sendOutbox(first);
        inOrder.verify(deliveryFacade).markOutboxPublished(1L);
        inOrder.verify(deliveryFacade).sendOutbox(second);
        inOrder.verify(deliveryFacade).markOutboxPublished(2L);
    }

    @Test
    @DisplayName("아웃박스 전송이 실패하면 그 건은 발행으로 표시하지 않고, 그 회차의 나머지도 보내지 않는다")
    void sendOutbox_stopsAtFirstFailure() {
        DeliveryOutbox first = outbox(1L);
        DeliveryOutbox second = outbox(2L);
        DeliveryOutbox third = outbox(3L);
        when(deliveryFacade.findUnpublishedOutboxes()).thenReturn(List.of(first, second, third));
        lenient().doThrow(new IllegalStateException("broker down")).when(deliveryFacade).sendOutbox(second);

        deliveryScheduler.sendOutbox();

        verify(deliveryFacade).markOutboxPublished(1L);
        verify(deliveryFacade, never()).markOutboxPublished(2L);
        verify(deliveryFacade, never()).sendOutbox(third);
    }

    @Test
    @DisplayName("발행하지 않은 아웃박스가 없으면 아무것도 보내지 않는다")
    void sendOutbox_noTargets() {
        when(deliveryFacade.findUnpublishedOutboxes()).thenReturn(List.of());

        deliveryScheduler.sendOutbox();

        verify(deliveryFacade, never()).sendOutbox(any());
        verify(deliveryFacade, never()).markOutboxPublished(anyLong());
    }

    private DeliveryOutbox outbox(Long id) {
        DeliveryOutbox outbox = new DeliveryOutbox("delivery.created", String.valueOf(id), "{}");
        ReflectionTestUtils.setField(outbox, "id", id);
        return outbox;
    }
}
