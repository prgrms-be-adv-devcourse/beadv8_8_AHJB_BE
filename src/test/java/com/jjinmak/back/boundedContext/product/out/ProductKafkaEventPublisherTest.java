package com.jjinmak.back.boundedContext.product.out;

import com.jjinmak.back.shared.product.event.ProductRegisteredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductKafkaEventPublisherTest {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    ProductKafkaEventPublisher productKafkaEventPublisher;

    @Test
    @DisplayName("상품 경매 등록 이벤트는 product.registered 토픽에 상품 ID를 키로 보낸다")
    void productRegistered_sendsWithProductIdKey() {
        ProductRegisteredEvent event = new ProductRegisteredEvent(
                1L, 2L, 3_000L, 10_000L, 50_000L, 3, null, "상품", "DIGITAL", null);

        productKafkaEventPublisher.publish(event);

        verify(kafkaTemplate).send("product.registered", "1", event);
    }
}
