package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.domain.OrderState;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.CONFIRMED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPING;
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
class OrderConfirmPurchaseUseCaseTest {

    @Mock
    OrderSupport orderSupport;

    @Mock
    EventPublisher eventPublisher;

    @InjectMocks
    OrderConfirmPurchaseUseCase orderConfirmPurchaseUseCase;

    OrderMember winner;
    OrderMember seller;

    @BeforeEach
    void setUp() {
        winner = new OrderMember(1L, UUID.randomUUID(), "낙찰자");
        seller = new OrderMember(2L, UUID.randomUUID(), "판매자");
    }

    @Test
    @DisplayName("배송 완료된 주문을 낙찰자가 구매 확정하면 CONFIRMED가 되고, 구매 확정 이벤트를 발행한다")
    void confirmPurchase_confirmsAndPublishes() {
        Order order = order(SHIPPED);
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(orderSupport.getOrderWithParticipants(100L)).thenReturn(order);

        LocalDateTime before = LocalDateTime.now();
        orderConfirmPurchaseUseCase.confirmPurchase(1L, 100L);
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
    @DisplayName("판매자가 구매 확정하면 ORDER_FORBIDDEN, 상태가 바뀌지 않고 이벤트를 발행하지 않는다")
    void confirmPurchase_bySeller() {
        Order order = order(SHIPPED);
        when(orderSupport.getMember(2L)).thenReturn(seller);
        when(orderSupport.getOrderWithParticipants(100L)).thenReturn(order);

        assertThatThrownBy(() -> orderConfirmPurchaseUseCase.confirmPurchase(2L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_FORBIDDEN);

        assertThat(order.getState()).isEqualTo(SHIPPED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("배송 중인 주문을 구매 확정하면 ORDER_STATE_BAD_REQUEST, 상태가 바뀌지 않고 이벤트를 발행하지 않는다")
    void confirmPurchase_notShipped() {
        Order order = order(SHIPPING);
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(orderSupport.getOrderWithParticipants(100L)).thenReturn(order);

        assertThatThrownBy(() -> orderConfirmPurchaseUseCase.confirmPurchase(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);

        assertThat(order.getState()).isEqualTo(SHIPPING);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("이미 구매 확정된 주문을 다시 구매 확정하면 ORDER_STATE_BAD_REQUEST, 이벤트를 발행하지 않는다")
    void confirmPurchase_alreadyConfirmed() {
        Order order = order(CONFIRMED);
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(orderSupport.getOrderWithParticipants(100L)).thenReturn(order);

        assertThatThrownBy(() -> orderConfirmPurchaseUseCase.confirmPurchase(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);

        assertThat(order.getState()).isEqualTo(CONFIRMED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("주문 회원이 없으면 USER_NOT_FOUND, 주문 조회와 이벤트 발행을 하지 않는다")
    void confirmPurchase_memberNotFound() {
        when(orderSupport.getMember(1L)).thenThrow(new BusinessException(USER_NOT_FOUND));

        assertThatThrownBy(() -> orderConfirmPurchaseUseCase.confirmPurchase(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        verify(orderSupport, never()).getOrderWithParticipants(any());
        verifyNoInteractions(eventPublisher);
    }

    private Order order(OrderState state) {
        Order order = new Order(new OrderGroup(winner, 33_000L), winner, seller, 10L, 20L, 30_000L, 3_000L);
        order.updateState(state);
        return order;
    }
}
