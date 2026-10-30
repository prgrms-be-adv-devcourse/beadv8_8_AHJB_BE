package com.jjinmak.back.boundedContext.delivery.out;

import com.jjinmak.back.shared.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.back.shared.delivery.event.DeliveryStatusUpdatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;

import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.DELIVERED;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.IN_TRANSIT;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryKafkaEventPublisherTest {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    DeliveryKafkaEventPublisher deliveryKafkaEventPublisher;

    @Test
    @DisplayName("배송 생성 이벤트는 delivery.created 토픽에 주문 ID를 키로 보낸다")
    void deliveryCreated_sendsWithOrderIdKey() {
        DeliveryCreatedEvent event = new DeliveryCreatedEvent(1L);

        deliveryKafkaEventPublisher.handle(event);

        verify(kafkaTemplate).send("delivery.created", "1", event);
    }

    @Test
    @DisplayName("배송 상태 변경 이벤트는 delivery.status-updated 토픽에 주문 ID를 키로 보낸다")
    void deliveryStatusUpdated_sendsWithOrderIdKey() {
        DeliveryStatusUpdatedEvent event = new DeliveryStatusUpdatedEvent(1L, IN_TRANSIT, DELIVERED, LocalDateTime.of(2026, 10, 30, 12, 0));

        deliveryKafkaEventPublisher.handle(event);

        verify(kafkaTemplate).send("delivery.status-updated", "1", event);
    }
}
