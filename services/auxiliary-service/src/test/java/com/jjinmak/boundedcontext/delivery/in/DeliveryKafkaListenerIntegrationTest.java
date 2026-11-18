package com.jjinmak.boundedcontext.delivery.in;

import com.jjinmak.boundedcontext.delivery.domain.Delivery;
import com.jjinmak.boundedcontext.delivery.domain.DeliveryMember;
import com.jjinmak.boundedcontext.delivery.out.deliveryTracker.DeliveryTracker;
import com.jjinmak.boundedcontext.delivery.out.repository.DeliveryMemberRepository;
import com.jjinmak.boundedcontext.delivery.out.repository.DeliveryRepository;
import com.jjinmak.contracts.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.contracts.order.event.OrderSendProductEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static com.jjinmak.contracts.delivery.domain.CarrierType.CJ;
import static com.jjinmak.contracts.delivery.domain.CarrierType.HANJIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:deliverykafkalistener;MODE=PostgreSQL")
class DeliveryKafkaListenerIntegrationTest {

    @Autowired
    DeliveryKafkaListener deliveryKafkaListener;

    @Autowired
    DeliveryRepository deliveryRepository;

    @Autowired
    DeliveryMemberRepository deliveryMemberRepository;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    DeliveryTracker deliveryTracker;

    @MockitoBean
    DeliveryScheduler deliveryScheduler;

    @BeforeEach
    void setUp() {
        deliveryMemberRepository.save(new DeliveryMember(1L, UUID.randomUUID(), "낙찰자"));
        deliveryMemberRepository.save(new DeliveryMember(2L, UUID.randomUUID(), "판매자"));
    }

    @AfterEach
    void tearDown() {
        deliveryRepository.deleteAllInBatch();
        deliveryMemberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("같은 상품 발송 이벤트를 다시 받아도 예외 없이 끝나고, 배송은 하나이며 배송 생성 이벤트를 Kafka로 다시 보내고 Webhook은 다시 등록하지 않는다")
    void sendProduct_twice_republishesCreatedEvent() {
        OrderSendProductEvent event = new OrderSendProductEvent(100L, 1L, 2L, CJ, "111");
        deliveryKafkaListener.handle(event);
        Long deliveryId = deliveryRepository.findByOrderId(100L).orElseThrow().getId();
        verify(deliveryTracker, timeout(2_000)).registerWebhook(eq(deliveryId), eq(100L), eq(CJ), eq("111"), any(UUID.class), eq(2));

        assertThatCode(() -> deliveryKafkaListener.handle(event))
                .doesNotThrowAnyException();

        assertThat(deliveryRepository.count()).isEqualTo(1);
        verify(kafkaTemplate, times(2)).send("delivery.created", "100", new DeliveryCreatedEvent(100L));
        verify(deliveryTracker, after(500).times(1)).registerWebhook(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("배송이 있는 주문에 다른 운송장으로 상품 발송 이벤트가 와도 처음 택배사·운송장이 그대로이고, 배송 생성 이벤트만 다시 보낸다")
    void sendProduct_otherWaybill_keepsFirstWaybill() {
        deliveryKafkaListener.handle(new OrderSendProductEvent(100L, 1L, 2L, CJ, "111"));

        deliveryKafkaListener.handle(new OrderSendProductEvent(100L, 1L, 2L, HANJIN, "222"));

        Delivery delivery = deliveryRepository.findByOrderId(100L).orElseThrow();
        assertThat(delivery.getCarrier()).isEqualTo(CJ);
        assertThat(delivery.getWaybill()).isEqualTo("111");
        verify(kafkaTemplate, times(2)).send("delivery.created", "100", new DeliveryCreatedEvent(100L));
    }
}
