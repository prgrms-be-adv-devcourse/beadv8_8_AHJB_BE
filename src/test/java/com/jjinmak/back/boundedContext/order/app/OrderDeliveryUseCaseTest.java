package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.domain.OrderState;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.back.shared.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.back.shared.order.event.OrderSendProductEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.PAID;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPING;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.WAITING;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_FORBIDDEN;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_STATE_BAD_REQUEST;
import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;
import static com.jjinmak.back.shared.delivery.domain.CarrierType.CJ;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.AVAILABLE_FOR_PICKUP;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.DELIVERED;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.IN_TRANSIT;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.OUT_FOR_DELIVERY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDeliveryUseCaseTest {

    @Mock
    OrderSupport orderSupport;

    @Mock
    EventPublisher eventPublisher;

    @InjectMocks
    OrderDeliveryUseCase orderDeliveryUseCase;

    OrderMember winner;
    OrderMember seller;

    LocalDateTime deliveredAt = LocalDateTime.of(2026, 10, 16, 14, 0);

    @BeforeEach
    void setUp() {
        winner = new OrderMember(1L, UUID.randomUUID(), "낙찰자");
        seller = new OrderMember(2L, UUID.randomUUID(), "판매자");
    }

    @Test
    @DisplayName("결제 완료된 주문에 판매자가 배송을 요청하면 운송장 정보를 담은 상품 발송 이벤트를 발행하고, 상태는 바꾸지 않는다")
    void requestDelivery_publishesSendProductEvent() {
        Order order = order(PAID);
        when(orderSupport.getMember(2L)).thenReturn(seller);
        when(orderSupport.getOrderWithParticipants(100L)).thenReturn(order);

        orderDeliveryUseCase.requestDelivery(2L, 100L, CJ, "1234567890");

        assertThat(order.getState()).isEqualTo(PAID);
        verify(eventPublisher).publish(new OrderSendProductEvent(100L, 1L, 2L, CJ, "1234567890"));
    }

    @Test
    @DisplayName("낙찰자가 배송을 요청하면 ORDER_FORBIDDEN, 이벤트를 발행하지 않는다")
    void requestDelivery_byWinner() {
        Order order = order(PAID);
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(orderSupport.getOrderWithParticipants(100L)).thenReturn(order);

        assertThatThrownBy(() -> orderDeliveryUseCase.requestDelivery(1L, 100L, CJ, "1234567890"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_FORBIDDEN);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("결제 완료 상태가 아닌 주문에 배송을 요청하면 ORDER_STATE_BAD_REQUEST, 상태가 바뀌지 않고 이벤트를 발행하지 않는다")
    void requestDelivery_notPaid() {
        Order order = order(SHIPPING);
        when(orderSupport.getMember(2L)).thenReturn(seller);
        when(orderSupport.getOrderWithParticipants(100L)).thenReturn(order);

        assertThatThrownBy(() -> orderDeliveryUseCase.requestDelivery(2L, 100L, CJ, "1234567890"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);

        assertThat(order.getState()).isEqualTo(SHIPPING);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("주문 회원이 없으면 USER_NOT_FOUND, 주문 조회와 이벤트 발행을 하지 않는다")
    void requestDelivery_memberNotFound() {
        when(orderSupport.getMember(2L)).thenThrow(new BusinessException(USER_NOT_FOUND));

        assertThatThrownBy(() -> orderDeliveryUseCase.requestDelivery(2L, 100L, CJ, "1234567890"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        verify(orderSupport, never()).getOrderWithParticipants(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("배송이 생성되면 결제 완료된 주문이 SHIPPING이 된다")
    void startDelivery_paidToShipping() {
        Order order = order(PAID);
        when(orderSupport.getOrder(100L)).thenReturn(order);

        orderDeliveryUseCase.startDelivery(new DeliveryCreatedEvent(100L));

        assertThat(order.getState()).isEqualTo(SHIPPING);
    }

    @Test
    @DisplayName("결제 완료 상태가 아닌 주문의 배송이 생성되면 ORDER_STATE_BAD_REQUEST, 상태가 바뀌지 않는다")
    void startDelivery_notPaid() {
        Order order = order(WAITING);
        when(orderSupport.getOrder(100L)).thenReturn(order);

        assertThatThrownBy(() -> orderDeliveryUseCase.startDelivery(new DeliveryCreatedEvent(100L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);

        assertThat(order.getState()).isEqualTo(WAITING);
    }

    @Test
    @DisplayName("배송 완료로 바뀌면 배송 중인 주문이 SHIPPED가 되고, 구매 확정 기한은 배송 완료 시각 + 7일이다")
    void updateDeliveryStatus_delivered() {
        Order order = order(SHIPPING);
        when(orderSupport.getOrder(100L)).thenReturn(order);

        orderDeliveryUseCase.updateDeliveryStatus(new DeliveryStatusUpdatedEvent(100L, OUT_FOR_DELIVERY, DELIVERED, deliveredAt));

        assertThat(order.getState()).isEqualTo(SHIPPED);
        assertThat(order.getConfirmExpiredAt()).isEqualTo(LocalDateTime.of(2026, 10, 23, 14, 0));
    }

    @Test
    @DisplayName("수령 가능으로 바뀌어도 배송 중인 주문이 SHIPPED가 되고, 구매 확정 기한은 바뀐 시각 + 7일이다")
    void updateDeliveryStatus_availableForPickup() {
        Order order = order(SHIPPING);
        when(orderSupport.getOrder(100L)).thenReturn(order);

        orderDeliveryUseCase.updateDeliveryStatus(new DeliveryStatusUpdatedEvent(100L, OUT_FOR_DELIVERY, AVAILABLE_FOR_PICKUP, deliveredAt));

        assertThat(order.getState()).isEqualTo(SHIPPED);
        assertThat(order.getConfirmExpiredAt()).isEqualTo(LocalDateTime.of(2026, 10, 23, 14, 0));
    }

    @Test
    @DisplayName("배송 완료나 수령 가능이 아닌 상태로 바뀌면 주문을 조회하지 않는다")
    void updateDeliveryStatus_otherStatus() {
        orderDeliveryUseCase.updateDeliveryStatus(new DeliveryStatusUpdatedEvent(100L, IN_TRANSIT, OUT_FOR_DELIVERY, deliveredAt));

        verifyNoInteractions(orderSupport);
    }

    @Test
    @DisplayName("이미 배송 완료된 주문에 배송 완료 이벤트가 다시 오면 예외 없이 무시하고, 구매 확정 기한을 바꾸지 않는다")
    void updateDeliveryStatus_alreadyShipped() {
        Order order = order(SHIPPING);
        order.shipped(deliveredAt);
        when(orderSupport.getOrder(100L)).thenReturn(order);

        orderDeliveryUseCase.updateDeliveryStatus(new DeliveryStatusUpdatedEvent(100L, DELIVERED, DELIVERED, deliveredAt.plusHours(1)));

        assertThat(order.getState()).isEqualTo(SHIPPED);
        assertThat(order.getConfirmExpiredAt()).isEqualTo(LocalDateTime.of(2026, 10, 23, 14, 0));
    }

    private Order order(OrderState state) {
        Order order = new Order(new OrderGroup(33_000L), winner, seller, 10L, 20L, 30_000L, 3_000L);
        order.updateState(state);
        return order;
    }
}
