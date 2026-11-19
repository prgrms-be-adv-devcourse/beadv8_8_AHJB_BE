package com.jjinmak.boundedcontext.delivery.out.outbox;

import com.jjinmak.boundedcontext.delivery.domain.DeliveryOutbox;
import com.jjinmak.boundedcontext.delivery.out.repository.DeliveryOutboxRepository;
import com.jjinmak.contracts.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.contracts.delivery.event.DeliveryStatusUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.DELIVERED;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.IN_TRANSIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryOutboxWriterTest {

    @Mock
    DeliveryOutboxRepository deliveryOutboxRepository;

    ObjectMapper objectMapper = JsonMapper.builder().build();

    DeliveryOutboxWriter deliveryOutboxWriter;

    @BeforeEach
    void setUp() {
        deliveryOutboxWriter = new DeliveryOutboxWriter(deliveryOutboxRepository, objectMapper);
    }

    @Test
    @DisplayName("배송 생성 이벤트는 delivery.created 토픽과 주문 ID 키로 아웃박스에 저장한다")
    void deliveryCreated_savesWithOrderIdKey() {
        DeliveryCreatedEvent event = new DeliveryCreatedEvent(1L);

        deliveryOutboxWriter.write(event);

        assertSaved("delivery.created", "1", event);
    }

    @Test
    @DisplayName("배송 상태 변경 이벤트는 delivery.status-updated 토픽과 주문 ID 키로 아웃박스에 저장한다")
    void deliveryStatusUpdated_savesWithOrderIdKey() {
        DeliveryStatusUpdatedEvent event = new DeliveryStatusUpdatedEvent(1L, 2L, 3L, IN_TRANSIT, DELIVERED, LocalDateTime.of(2026, 10, 30, 12, 0));

        deliveryOutboxWriter.write(event);

        assertSaved("delivery.status-updated", "1", event);
    }

    private void assertSaved(String topic, String key, Object event) {
        ArgumentCaptor<DeliveryOutbox> captor = ArgumentCaptor.forClass(DeliveryOutbox.class);
        verify(deliveryOutboxRepository).save(captor.capture());
        DeliveryOutbox saved = captor.getValue();

        assertThat(saved.getTopic()).isEqualTo(topic);
        assertThat(saved.getMessageKey()).isEqualTo(key);
        assertThat(objectMapper.readValue(saved.getPayload(), event.getClass())).isEqualTo(event);
        assertThat(saved.getPublishedAt()).isNull();
    }
}
