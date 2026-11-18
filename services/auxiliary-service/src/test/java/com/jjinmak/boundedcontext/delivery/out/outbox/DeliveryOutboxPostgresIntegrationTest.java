package com.jjinmak.boundedcontext.delivery.out.outbox;

import com.jjinmak.boundedcontext.delivery.app.DeliveryFacade;
import com.jjinmak.boundedcontext.delivery.domain.Delivery;
import com.jjinmak.boundedcontext.delivery.domain.DeliveryMember;
import com.jjinmak.boundedcontext.delivery.domain.DeliveryOutbox;
import com.jjinmak.boundedcontext.delivery.in.DeliveryScheduler;
import com.jjinmak.boundedcontext.delivery.out.deliveryTracker.DeliveryTracker;
import com.jjinmak.boundedcontext.delivery.out.dto.DeliveryStatusResponseDto;
import com.jjinmak.boundedcontext.delivery.out.repository.DeliveryMemberRepository;
import com.jjinmak.boundedcontext.delivery.out.repository.DeliveryOutboxRepository;
import com.jjinmak.boundedcontext.delivery.out.repository.DeliveryRepository;
import com.jjinmak.contracts.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.contracts.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.contracts.order.event.OrderSendProductEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.jjinmak.contracts.delivery.domain.CarrierType.CJ;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.IN_TRANSIT;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.WAITING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false"
})
@Import(DeliveryOutboxPostgresIntegrationTest.PostgresContainerConfig.class)
class DeliveryOutboxPostgresIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresContainerConfig {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:17-alpine");
        }
    }

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    DeliveryScheduler scheduledDeliveryScheduler;

    @MockitoBean
    DeliveryTracker deliveryTracker;

    @Autowired
    DeliveryFacade deliveryFacade;

    @Autowired
    DeliveryMemberRepository deliveryMemberRepository;

    @Autowired
    DeliveryRepository deliveryRepository;

    @Autowired
    DeliveryOutboxRepository deliveryOutboxRepository;

    @Autowired
    ObjectMapper objectMapper;

    DeliveryMember winner;
    DeliveryMember seller;

    @BeforeEach
    void setUp() {
        winner = deliveryMemberRepository.save(new DeliveryMember(1L, UUID.randomUUID(), "낙찰자"));
        seller = deliveryMemberRepository.save(new DeliveryMember(2L, UUID.randomUUID(), "판매자"));
    }

    @AfterEach
    void tearDown() {
        deliveryOutboxRepository.deleteAllInBatch();
        deliveryRepository.deleteAllInBatch();
        deliveryMemberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("PostgreSQL에서 상품 발송 이벤트로 배송을 만들면 배송 생성 이벤트가 아웃박스에 저장된다")
    void createDelivery_savesCreatedOutbox() {
        deliveryFacade.createDelivery(new OrderSendProductEvent(100L, 1L, 2L, CJ, "1111"));

        List<DeliveryOutbox> saved = deliveryOutboxRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getTopic()).isEqualTo("delivery.created");
        assertThat(saved.getFirst().getMessageKey()).isEqualTo("100");
        assertThat(objectMapper.readValue(saved.getFirst().getPayload(), DeliveryCreatedEvent.class)).isEqualTo(new DeliveryCreatedEvent(100L));
        assertThat(saved.getFirst().getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("PostgreSQL에서 택배사 조회로 배송 상태가 바뀌면 배송 상태 변경 이벤트가 아웃박스에 저장된다")
    void statusChanged_savesStatusUpdatedOutbox() {
        Long deliveryId = deliveryRepository.save(new Delivery(100L, winner, seller, CJ, "1111")).getId();
        OffsetDateTime eventTime = OffsetDateTime.of(2026, 10, 30, 12, 0, 0, 0, ZoneOffset.ofHours(9));
        when(deliveryTracker.trackLastStatus(CJ, "1111")).thenReturn(new DeliveryStatusResponseDto(
                new DeliveryStatusResponseDto.Data(new DeliveryStatusResponseDto.Track(
                        new DeliveryStatusResponseDto.LastEvent(eventTime, new DeliveryStatusResponseDto.Status(IN_TRANSIT)))),
                null));

        deliveryFacade.getDeliveryStatus(deliveryId);

        List<DeliveryOutbox> saved = deliveryOutboxRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getTopic()).isEqualTo("delivery.status-updated");
        assertThat(saved.getFirst().getMessageKey()).isEqualTo("100");
        assertThat(objectMapper.readValue(saved.getFirst().getPayload(), DeliveryStatusUpdatedEvent.class)).isEqualTo(
                new DeliveryStatusUpdatedEvent(100L, 1L, 2L, WAITING, IN_TRANSIT, eventTime.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()));
    }
}
