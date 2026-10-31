package com.jjinmak.back.boundedContext.delivery.app;

import com.jjinmak.back.boundedContext.delivery.domain.Delivery;
import com.jjinmak.back.boundedContext.delivery.domain.DeliveryMember;
import com.jjinmak.back.boundedContext.delivery.in.DeliveryScheduler;
import com.jjinmak.back.boundedContext.delivery.out.deliveryTracker.DeliveryTracker;
import com.jjinmak.back.boundedContext.delivery.out.dto.DeliveryStatusResponseDto;
import com.jjinmak.back.boundedContext.delivery.out.repository.DeliveryMemberRepository;
import com.jjinmak.back.boundedContext.delivery.out.repository.DeliveryRepository;
import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.domain.OrderState;
import com.jjinmak.back.boundedContext.order.out.repository.OrderGroupRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderMemberRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderRepository;
import com.jjinmak.back.shared.delivery.domain.CarrierType;
import com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.DELIVERY_ERROR;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPING;
import static com.jjinmak.back.shared.delivery.domain.CarrierType.CJ;
import static com.jjinmak.back.shared.delivery.domain.CarrierType.HANJIN;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.AT_PICKUP;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.DELIVERED;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.EXCEPTION;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.IN_TRANSIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
class DeliveryStatusSyncIntegrationTest {

    @Autowired
    DeliveryFacade deliveryFacade;

    @Autowired
    DeliveryRepository deliveryRepository;

    @Autowired
    DeliveryMemberRepository deliveryMemberRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderGroupRepository orderGroupRepository;

    @Autowired
    OrderMemberRepository orderMemberRepository;

    @MockitoBean
    DeliveryTracker deliveryTracker;

    @MockitoBean
    DeliveryScheduler deliveryScheduler;

    OffsetDateTime eventTime = OffsetDateTime.of(2026, 10, 19, 15, 0, 0, 0, ZoneOffset.ofHours(9));

    OrderMember winner;
    OrderMember seller;
    DeliveryMember deliveryWinner;
    DeliveryMember deliverySeller;

    @BeforeEach
    void setUp() {
        winner = orderMemberRepository.save(new OrderMember(1L, UUID.randomUUID(), "낙찰자"));
        seller = orderMemberRepository.save(new OrderMember(2L, UUID.randomUUID(), "판매자"));
        deliveryWinner = deliveryMemberRepository.save(new DeliveryMember(1L, UUID.randomUUID(), "낙찰자"));
        deliverySeller = deliveryMemberRepository.save(new DeliveryMember(2L, UUID.randomUUID(), "판매자"));
    }

    @AfterEach
    void tearDown() {
        deliveryRepository.deleteAllInBatch();
        deliveryMemberRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        orderGroupRepository.deleteAllInBatch();
        orderMemberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("배송 중 문제(EXCEPTION)가 생겼다고 조회되면 배송은 EXCEPTION, 배송 중인 주문은 DELIVERY_ERROR가 된다")
    void exceptionWhileShipping() {
        Long orderId = saveOrder(SHIPPING);
        Long deliveryId = saveDelivery(orderId, IN_TRANSIT, "1111");
        tracked(CJ, "1111", EXCEPTION);

        deliveryFacade.getDeliveryStatus(deliveryId);

        assertThat(deliveryRepository.findById(deliveryId).orElseThrow().getStatus()).isEqualTo(EXCEPTION);
        assertThat(orderRepository.findById(orderId).orElseThrow().getState()).isEqualTo(DELIVERY_ERROR);
    }

    @Test
    @DisplayName("EXCEPTION이던 배송이 바로 배송 완료로 조회되면 배송 오류 주문은 SHIPPED가 되고, 구매 확정 기한은 배송 완료를 반영한 시각 + 7일이다")
    void deliveredRightAfterException() {
        Long orderId = saveOrder(DELIVERY_ERROR);
        Long deliveryId = saveDelivery(orderId, EXCEPTION, "2222");
        tracked(CJ, "2222", DELIVERED);

        LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        deliveryFacade.getDeliveryStatus(deliveryId);
        LocalDateTime after = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);

        assertThat(deliveryRepository.findById(deliveryId).orElseThrow().getStatus()).isEqualTo(DELIVERED);
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getState()).isEqualTo(SHIPPED);
        assertThat(order.getConfirmExpiredAt()).isBetween(before.plusDays(7), after.plusDays(7));
    }

    @Test
    @DisplayName("배송 오류 주문의 운송장을 판매자가 바꾸면, 새 운송장의 첫 상태 변경에서 SHIPPING으로, 배송 완료에서 SHIPPED로 바뀐다")
    void reshipWithNewWaybill() {
        Long orderId = saveOrder(DELIVERY_ERROR);
        Long deliveryId = saveDelivery(orderId, EXCEPTION, "3333");

        deliveryFacade.updateDeliveryData(2L, orderId, HANJIN, "4444");

        assertThat(deliveryRepository.findById(deliveryId).orElseThrow().getStatus()).isEqualTo(DeliveryStatusCode.WAITING);
        assertThat(orderRepository.findById(orderId).orElseThrow().getState()).isEqualTo(DELIVERY_ERROR);

        tracked(HANJIN, "4444", AT_PICKUP);
        deliveryFacade.getDeliveryStatus(deliveryId);

        assertThat(orderRepository.findById(orderId).orElseThrow().getState()).isEqualTo(SHIPPING);

        tracked(HANJIN, "4444", DELIVERED);
        deliveryFacade.getDeliveryStatus(deliveryId);

        assertThat(orderRepository.findById(orderId).orElseThrow().getState()).isEqualTo(SHIPPED);
    }

    private Long saveOrder(OrderState state) {
        OrderGroup group = orderGroupRepository.save(new OrderGroup(winner, 33_000L));
        Order order = new Order(group, winner, seller, 10L, 20L, 30_000L, 3_000L);
        order.updateState(state);
        return orderRepository.save(order).getId();
    }

    private Long saveDelivery(Long orderId, DeliveryStatusCode status, String waybill) {
        Delivery delivery = new Delivery(orderId, deliveryWinner, deliverySeller, CJ, waybill);
        delivery.updateStatus(status);
        return deliveryRepository.save(delivery).getId();
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
