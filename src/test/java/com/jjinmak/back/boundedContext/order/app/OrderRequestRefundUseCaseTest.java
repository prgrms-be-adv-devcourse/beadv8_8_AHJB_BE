package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.domain.OrderState;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.order.event.OrderRefundRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.CONFIRMED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.REFUND_REQUESTED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPED;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_FORBIDDEN;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_STATE_BAD_REQUEST;
import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderRequestRefundUseCaseTest {

    @Mock
    OrderSupport orderSupport;

    @Mock
    EventPublisher eventPublisher;

    @InjectMocks
    OrderRequestRefundUseCase orderRequestRefundUseCase;

    OrderMember winner;
    OrderMember seller;

    @BeforeEach
    void setUp() {
        winner = new OrderMember(1L, UUID.randomUUID(), "낙찰자");
        seller = new OrderMember(2L, UUID.randomUUID(), "판매자");
    }

    @Test
    @DisplayName("배송 완료된 주문에 낙찰자가 환불을 요청하면 REFUND_REQUESTED가 되고, 환불 요청 이벤트를 발행한다")
    void requestRefund_requestsAndPublishes() {
        Order order = order(SHIPPED);
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(orderSupport.getOrderWithParticipants(100L)).thenReturn(order);

        orderRequestRefundUseCase.requestRefund(1L, 100L);

        assertThat(order.getState()).isEqualTo(REFUND_REQUESTED);
        verify(eventPublisher).publish(new OrderRefundRequestedEvent(100L, 1L, 2L, 20L, 30_000L, 3_000L));
    }

    @Test
    @DisplayName("판매자가 환불을 요청하면 ORDER_FORBIDDEN, 상태가 바뀌지 않고 이벤트를 발행하지 않는다")
    void requestRefund_bySeller() {
        Order order = order(SHIPPED);
        when(orderSupport.getMember(2L)).thenReturn(seller);
        when(orderSupport.getOrderWithParticipants(100L)).thenReturn(order);

        assertThatThrownBy(() -> orderRequestRefundUseCase.requestRefund(2L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_FORBIDDEN);

        assertThat(order.getState()).isEqualTo(SHIPPED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("구매 확정된 주문에 환불을 요청하면 ORDER_STATE_BAD_REQUEST, 상태가 바뀌지 않고 이벤트를 발행하지 않는다")
    void requestRefund_confirmed() {
        Order order = order(CONFIRMED);
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(orderSupport.getOrderWithParticipants(100L)).thenReturn(order);

        assertThatThrownBy(() -> orderRequestRefundUseCase.requestRefund(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);

        assertThat(order.getState()).isEqualTo(CONFIRMED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("이미 환불을 요청한 주문에 다시 요청하면 ORDER_STATE_BAD_REQUEST, 이벤트를 발행하지 않는다")
    void requestRefund_alreadyRequested() {
        Order order = order(REFUND_REQUESTED);
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(orderSupport.getOrderWithParticipants(100L)).thenReturn(order);

        assertThatThrownBy(() -> orderRequestRefundUseCase.requestRefund(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);

        assertThat(order.getState()).isEqualTo(REFUND_REQUESTED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("주문 회원이 없으면 USER_NOT_FOUND, 주문 조회와 이벤트 발행을 하지 않는다")
    void requestRefund_memberNotFound() {
        when(orderSupport.getMember(1L)).thenThrow(new BusinessException(USER_NOT_FOUND));

        assertThatThrownBy(() -> orderRequestRefundUseCase.requestRefund(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        verify(orderSupport, never()).getOrderWithParticipants(any());
        verifyNoInteractions(eventPublisher);
    }

    private Order order(OrderState state) {
        Order order = new Order(new OrderGroup(33_000L), winner, seller, 10L, 20L, 30_000L, 3_000L);
        order.updateState(state);
        return order;
    }
}
