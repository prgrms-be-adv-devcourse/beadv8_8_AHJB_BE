package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.domain.OrderState;
import com.jjinmak.back.boundedContext.order.out.repository.OrderGroupRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.shared.order.event.OrderRefundRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.CONFIRMED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.PAID;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.REFUND_REQUESTED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.WAITING;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_STATE_BAD_REQUEST;
import static com.jjinmak.back.shared.order.event.OrderRefundRequestedEvent.RefundType.SYSTEM_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSchedulerUseCaseTest {

    @Mock
    EventPublisher eventPublisher;

    @Mock
    OrderRepository orderRepository;

    @Mock
    OrderGroupRepository orderGroupRepository;

    @Mock
    OrderSupport orderSupport;

    @InjectMocks
    OrderSchedulerUseCase orderSchedulerUseCase;

    OrderMember winner;
    OrderMember seller;

    @BeforeEach
    void setUp() {
        winner = new OrderMember(1L, UUID.randomUUID(), "낙찰자");
        seller = new OrderMember(2L, UUID.randomUUID(), "판매자");
    }

    @Test
    @DisplayName("구매 확정 기한이 지금보다 앞선 주문을 찾는다")
    void getExpiredOrderIds_usesNow() {
        when(orderRepository.findExpiredOrderIds(any(LocalDateTime.class))).thenReturn(List.of(100L, 101L));

        LocalDateTime before = LocalDateTime.now();
        List<Long> ids = orderSchedulerUseCase.getExpiredOrderIds();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository).findExpiredOrderIds(captor.capture());

        assertThat(captor.getValue()).isBetween(before, after);
        assertThat(ids).containsExactly(100L, 101L);
    }

    @Test
    @DisplayName("배송 완료된 주문을 자동으로 구매 확정하면 CONFIRMED가 되고, 구매 확정 이벤트를 발행한다")
    void confirmPurchaseAuto_confirmsAndPublishes() {
        Order order = order(new OrderGroup(33_000L), 100L, SHIPPED);
        when(orderSupport.getOrderWithParticipants(100L)).thenReturn(order);

        LocalDateTime before = LocalDateTime.now();
        orderSchedulerUseCase.confirmPurchaseAuto(100L);
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<OrderConfirmPurchaseEvent> captor = ArgumentCaptor.forClass(OrderConfirmPurchaseEvent.class);
        verify(eventPublisher).publish(captor.capture());

        OrderConfirmPurchaseEvent event = captor.getValue();
        assertThat(order.getState()).isEqualTo(CONFIRMED);
        assertThat(event.orderId()).isEqualTo(100L);
        assertThat(event.winnerId()).isEqualTo(1L);
        assertThat(event.sellerId()).isEqualTo(2L);
        assertThat(event.auctionId()).isEqualTo(10L);
        assertThat(event.productId()).isEqualTo(20L);
        assertThat(event.winningPrice()).isEqualTo(30_000L);
        assertThat(event.deliveryFee()).isEqualTo(3_000L);
        assertThat(event.confirmedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("그 사이 낙찰자가 환불을 요청한 주문이면 ORDER_STATE_BAD_REQUEST, 상태가 바뀌지 않고 이벤트를 발행하지 않는다")
    void confirmPurchaseAuto_refundRequested() {
        Order order = order(new OrderGroup(33_000L), 100L, REFUND_REQUESTED);
        when(orderSupport.getOrderWithParticipants(100L)).thenReturn(order);

        assertThatThrownBy(() -> orderSchedulerUseCase.confirmPurchaseAuto(100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);

        assertThat(order.getState()).isEqualTo(REFUND_REQUESTED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("결제 대기가 1시간 넘게 지난 주문을 찾는다")
    void deleteOverWaitingOrders_usesOneHourAgo() {
        when(orderRepository.findOverWaitedAll(any(LocalDateTime.class))).thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now().minusHours(1);
        orderSchedulerUseCase.deleteOverWaitingOrders();
        LocalDateTime after = LocalDateTime.now().minusHours(1);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository).findOverWaitedAll(captor.capture());

        assertThat(captor.getValue()).isBetween(before, after);
    }

    @Test
    @DisplayName("결제 대기가 오래된 주문들의 주문 그룹을 중복 없이 삭제한다")
    void deleteOverWaitingOrders_deletesDistinctGroups() {
        OrderGroup groupA = new OrderGroup(66_000L);
        OrderGroup groupB = new OrderGroup(33_000L);
        Order a1 = order(groupA, 100L, WAITING);
        Order a2 = order(groupA, 101L, WAITING);
        Order b1 = order(groupB, 102L, WAITING);
        when(orderRepository.findOverWaitedAll(any(LocalDateTime.class))).thenReturn(List.of(a1, a2, b1));

        orderSchedulerUseCase.deleteOverWaitingOrders();

        verify(orderGroupRepository).deleteAll(Set.of(groupA, groupB));
    }

    @Test
    @DisplayName("결제 후 5일이 지나도록 발송되지 않은 주문을 찾는다")
    void getNonShippedOrderIds_usesFiveDaysAgo() {
        when(orderRepository.findNonShippedOrderIds(any(LocalDateTime.class))).thenReturn(List.of(100L, 101L));

        LocalDateTime before = LocalDateTime.now().minusDays(5);
        List<Long> ids = orderSchedulerUseCase.getNonShippedOrderIds();
        LocalDateTime after = LocalDateTime.now().minusDays(5);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository).findNonShippedOrderIds(captor.capture());

        assertThat(captor.getValue()).isBetween(before, after);
        assertThat(ids).containsExactly(100L, 101L);
    }

    @Test
    @DisplayName("결제 완료 주문에 시스템 환불을 요청하면 REFUND_REQUESTED가 되고, 시스템이 요청한 환불 요청 이벤트(SYSTEM_REQUEST)를 발행한다")
    void requestSystemRefund_requestsAndPublishes() {
        Order order = order(new OrderGroup(33_000L), 100L, PAID);
        when(orderSupport.getOrderWithParticipants(100L)).thenReturn(order);

        orderSchedulerUseCase.requestSystemRefund(100L);

        assertThat(order.getState()).isEqualTo(REFUND_REQUESTED);
        verify(eventPublisher).publish(new OrderRefundRequestedEvent(100L, 1L, 2L, 20L, 30_000L, 3_000L, SYSTEM_REQUEST));
    }

    @ParameterizedTest(name = "{0} 주문에 시스템 환불 요청")
    @EnumSource(value = OrderState.class, names = "PAID", mode = EXCLUDE)
    @DisplayName("그 사이 발송이 시작됐거나 이미 환불을 요청한 주문처럼 결제 완료 상태가 아니면 ORDER_STATE_BAD_REQUEST, 상태가 바뀌지 않고 이벤트를 발행하지 않는다")
    void requestSystemRefund_notPaid(OrderState state) {
        Order order = order(new OrderGroup(33_000L), 100L, state);
        when(orderSupport.getOrderWithParticipants(100L)).thenReturn(order);

        assertThatThrownBy(() -> orderSchedulerUseCase.requestSystemRefund(100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);

        assertThat(order.getState()).isEqualTo(state);
        verifyNoInteractions(eventPublisher);
    }

    private Order order(OrderGroup group, Long id, OrderState state) {
        Order order = new Order(group, winner, seller, 10L, 20L, 30_000L, 3_000L);
        order.updateState(state);
        ReflectionTestUtils.setField(order, "id", id);
        group.add(order);
        return order;
    }
}
