package com.jjinmak.delivery.app;

import com.jjinmak.contracts.delivery.domain.CarrierType;
import com.jjinmak.contracts.delivery.domain.DeliveryStatusCode;
import com.jjinmak.contracts.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.delivery.app.event.WebhookRegisterEvent;
import com.jjinmak.delivery.domain.Delivery;
import com.jjinmak.delivery.domain.DeliveryMember;
import com.jjinmak.delivery.in.DeliveryScheduler;
import com.jjinmak.delivery.out.deliveryTracker.DeliveryTracker;
import com.jjinmak.delivery.out.dto.DeliveryStatusResponseDto;
import com.jjinmak.delivery.out.repository.DeliveryMemberRepository;
import com.jjinmak.delivery.out.repository.DeliveryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static com.jjinmak.contracts.delivery.domain.CarrierType.CJ;
import static com.jjinmak.contracts.delivery.domain.CarrierType.HANJIN;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.AT_PICKUP;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.DELIVERED;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.EXCEPTION;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.IN_TRANSIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@RecordApplicationEvents
class DeliveryStatusSyncIntegrationTest {

    @Autowired
    DeliveryFacade deliveryFacade;

    @Autowired
    DeliveryRepository deliveryRepository;

    @Autowired
    DeliveryMemberRepository deliveryMemberRepository;

    @Autowired
    ApplicationEvents events;

    @MockitoBean
    DeliveryTracker deliveryTracker;

    @MockitoBean
    DeliveryScheduler deliveryScheduler;

    OffsetDateTime eventTime = OffsetDateTime.of(2026, 10, 19, 15, 0, 0, 0, ZoneOffset.ofHours(9));

    LocalDateTime updatedAt = eventTime.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();

    DeliveryMember winner;
    DeliveryMember seller;

    @BeforeEach
    void setUp() {
        winner = deliveryMemberRepository.save(new DeliveryMember(1L, UUID.randomUUID(), "낙찰자"));
        seller = deliveryMemberRepository.save(new DeliveryMember(2L, UUID.randomUUID(), "판매자"));
    }

    @AfterEach
    void tearDown() {
        deliveryRepository.deleteAllInBatch();
        deliveryMemberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("배송 중 문제(EXCEPTION)가 생겼다고 조회되면 배송은 EXCEPTION이 되고, 주문 id·낙찰자 id·판매자 id와 IN_TRANSIT → EXCEPTION을 담은 배송 상태 변경 이벤트를 택배사 이벤트 시각으로 발행한다")
    void exceptionWhileShipping() {
        Long deliveryId = saveDelivery(100L, IN_TRANSIT, "1111");
        tracked(CJ, "1111", EXCEPTION);

        deliveryFacade.getDeliveryStatus(deliveryId);

        assertThat(statusOf(deliveryId)).isEqualTo(EXCEPTION);
        assertThat(events.stream(DeliveryStatusUpdatedEvent.class))
                .containsExactly(new DeliveryStatusUpdatedEvent(100L, 1L, 2L, IN_TRANSIT, EXCEPTION, updatedAt));
    }

    @Test
    @DisplayName("EXCEPTION이던 배송이 바로 배송 완료로 조회되면 배송은 DELIVERED가 되고, EXCEPTION → DELIVERED 배송 상태 변경 이벤트를 발행한다")
    void deliveredRightAfterException() {
        Long deliveryId = saveDelivery(100L, EXCEPTION, "2222");
        tracked(CJ, "2222", DELIVERED);

        deliveryFacade.getDeliveryStatus(deliveryId);

        assertThat(statusOf(deliveryId)).isEqualTo(DELIVERED);
        assertThat(events.stream(DeliveryStatusUpdatedEvent.class))
                .containsExactly(new DeliveryStatusUpdatedEvent(100L, 1L, 2L, EXCEPTION, DELIVERED, updatedAt));
    }

    @Test
    @DisplayName("판매자가 EXCEPTION인 배송의 운송장을 바꾸면 WAITING이 되어 새 운송장으로 Webhook 등록 이벤트를 발행하고, 새 운송장의 첫 상태 변경과 배송 완료에서 WAITING → AT_PICKUP, AT_PICKUP → DELIVERED 배송 상태 변경 이벤트를 차례로 발행한다")
    void reshipWithNewWaybill() {
        Long deliveryId = saveDelivery(100L, EXCEPTION, "3333");

        deliveryFacade.updateDeliveryData(2L, 100L, HANJIN, "4444");

        assertThat(statusOf(deliveryId)).isEqualTo(DeliveryStatusCode.WAITING);
        assertThat(events.stream(WebhookRegisterEvent.class))
                .containsExactly(new WebhookRegisterEvent(deliveryId, 100L, HANJIN, "4444"));

        tracked(HANJIN, "4444", AT_PICKUP);
        deliveryFacade.getDeliveryStatus(deliveryId);

        tracked(HANJIN, "4444", DELIVERED);
        deliveryFacade.getDeliveryStatus(deliveryId);

        assertThat(statusOf(deliveryId)).isEqualTo(DELIVERED);
        assertThat(events.stream(DeliveryStatusUpdatedEvent.class))
                .containsExactly(
                        new DeliveryStatusUpdatedEvent(100L, 1L, 2L, DeliveryStatusCode.WAITING, AT_PICKUP, updatedAt),
                        new DeliveryStatusUpdatedEvent(100L, 1L, 2L, AT_PICKUP, DELIVERED, updatedAt));
    }

    private Long saveDelivery(Long orderId, DeliveryStatusCode status, String waybill) {
        Delivery delivery = new Delivery(orderId, winner, seller, CJ, waybill);
        delivery.updateStatus(status);
        return deliveryRepository.save(delivery).getId();
    }

    private DeliveryStatusCode statusOf(Long deliveryId) {
        return deliveryRepository.findById(deliveryId).orElseThrow().getStatus();
    }

    private void tracked(CarrierType carrier, String waybill, DeliveryStatusCode code) {
        when(deliveryTracker.trackLastStatus(carrier, waybill)).thenReturn(
                new DeliveryStatusResponseDto(
                        new DeliveryStatusResponseDto.Data(
                                new DeliveryStatusResponseDto.Track(
                                        new DeliveryStatusResponseDto.LastEvent(eventTime, new DeliveryStatusResponseDto.Status(code))
                                )
                        ),
                        null
                )
        );
    }
}
