package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.OrderGroupRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.order.event.OrderPaymentSucceedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.FAILED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.PAID;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_GROUP_NOT_FOUND;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_STATE_BAD_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentResultUseCaseTest {

    @Mock
    OrderGroupRepository orderGroupRepository;

    @Mock
    EventPublisher eventPublisher;

    @InjectMocks
    OrderPaymentResultUseCase orderPaymentResultUseCase;

    OrderMember winner;
    OrderMember seller;

    OrderGroup group;

    Order first;
    Order second;

    @BeforeEach
    void setUp() {
        winner = new OrderMember(1L, UUID.randomUUID(), "낙찰자");
        seller = new OrderMember(2L, UUID.randomUUID(), "판매자");
        group = new OrderGroup(87_000L);
        first = order(100L, 10L, 20L, 30_000L, 3_000L);
        second = order(101L, 11L, 21L, 50_000L, 4_000L);
    }

    @Test
    @DisplayName("결제에 성공하면 그룹의 모든 주문이 PAID가 되고, 주문마다 같은 결제 시각으로 결제 성공 이벤트를 발행한다")
    void paymentSucceed_paysAllOrdersAndPublishesEvents() {
        when(orderGroupRepository.findByIdWithOrders(50L)).thenReturn(Optional.of(group));

        LocalDateTime before = LocalDateTime.now();
        orderPaymentResultUseCase.paymentSucceed(50L);
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<OrderPaymentSucceedEvent> captor = ArgumentCaptor.forClass(OrderPaymentSucceedEvent.class);
        verify(eventPublisher, times(2)).publish(captor.capture());

        LocalDateTime paidAt = captor.getAllValues().get(0).paidAt();
        assertThat(first.getState()).isEqualTo(PAID);
        assertThat(second.getState()).isEqualTo(PAID);
        assertThat(paidAt).isBetween(before, after);
        assertThat(captor.getAllValues()).containsExactly(
                new OrderPaymentSucceedEvent(100L, 10L, 20L, paidAt),
                new OrderPaymentSucceedEvent(101L, 11L, 21L, paidAt)
        );
    }

    @Test
    @DisplayName("결제 성공한 주문 그룹이 없으면 ORDER_GROUP_NOT_FOUND, 이벤트를 발행하지 않는다")
    void paymentSucceed_groupNotFound() {
        when(orderGroupRepository.findByIdWithOrders(50L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderPaymentResultUseCase.paymentSucceed(50L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_GROUP_NOT_FOUND);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("결제에 실패하면 결제 대기 중인 그룹의 모든 주문이 FAILED가 되고, 이벤트를 발행하지 않는다")
    void paymentFailed_failsAllWaitingOrders() {
        when(orderGroupRepository.findByIdWithOrders(50L)).thenReturn(Optional.of(group));

        orderPaymentResultUseCase.paymentFailed(50L);

        assertThat(first.getState()).isEqualTo(FAILED);
        assertThat(second.getState()).isEqualTo(FAILED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("이미 결제된 주문이 섞인 그룹에 결제 실패가 오면 ORDER_STATE_BAD_REQUEST, 결제된 주문은 PAID 그대로이고 이벤트를 발행하지 않는다")
    void paymentFailed_alreadyPaid() {
        first.updateState(PAID);
        when(orderGroupRepository.findByIdWithOrders(50L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> orderPaymentResultUseCase.paymentFailed(50L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);

        assertThat(first.getState()).isEqualTo(PAID);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("결제 실패한 주문 그룹이 없으면 ORDER_GROUP_NOT_FOUND, 이벤트를 발행하지 않는다")
    void paymentFailed_groupNotFound() {
        when(orderGroupRepository.findByIdWithOrders(50L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderPaymentResultUseCase.paymentFailed(50L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_GROUP_NOT_FOUND);

        verifyNoInteractions(eventPublisher);
    }

    private Order order(Long id, Long auctionId, Long productId, Long winningPrice, Long deliveryFee) {
        Order order = new Order(group, winner, seller, auctionId, productId, winningPrice, deliveryFee);
        ReflectionTestUtils.setField(order, "id", id);
        group.add(order);
        return order;
    }
}
