package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.domain.OrderState;
import com.jjinmak.back.boundedContext.order.out.OrderRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.shared.order.event.OrderRefundAcceptedEvent;
import com.jjinmak.back.shared.refund.dto.RefundDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.CONFIRMED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.REFUNDED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.REFUND_REQUESTED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPED;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_NOT_FOUND;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_STATE_BAD_REQUEST;
import static com.jjinmak.back.shared.refund.domain.RefundStatus.ACCEPTED;
import static com.jjinmak.back.shared.refund.domain.RefundStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderRefundResultUseCaseTest {

    @Mock
    OrderSupport orderSupport;

    @Mock
    OrderRepository orderRepository;

    @Mock
    EventPublisher eventPublisher;

    @InjectMocks
    OrderRefundResultUseCase orderRefundResultUseCase;

    OrderMember winner;
    OrderMember seller;

    LocalDateTime requestedAt = LocalDateTime.of(2026, 10, 14, 12, 0);
    LocalDateTime processedAt = LocalDateTime.of(2026, 10, 15, 12, 0);

    RefundDto accepted = new RefundDto(7L, 100L, 1L, ACCEPTED, 33_000L, requestedAt, processedAt);
    RefundDto rejected = new RefundDto(7L, 100L, 1L, REJECTED, 33_000L, requestedAt, processedAt);

    @BeforeEach
    void setUp() {
        winner = new OrderMember(1L, UUID.randomUUID(), "낙찰자");
        seller = new OrderMember(2L, UUID.randomUUID(), "판매자");
    }

    @Test
    @DisplayName("환불이 승인되면 환불 요청 상태의 주문이 REFUNDED가 되고, 환불 승인 이벤트를 발행한다")
    void acceptRefund_refundsAndPublishes() {
        Order order = order(REFUND_REQUESTED);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        orderRefundResultUseCase.acceptRefund(accepted);

        assertThat(order.getState()).isEqualTo(REFUNDED);
        verify(eventPublisher).publish(new OrderRefundAcceptedEvent(100L, 10L));
    }

    @Test
    @DisplayName("환불이 승인된 주문이 없으면 ORDER_NOT_FOUND, 이벤트를 발행하지 않는다")
    void acceptRefund_orderNotFound() {
        when(orderRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderRefundResultUseCase.acceptRefund(accepted))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_NOT_FOUND);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("환불 요청 상태가 아닌 주문의 환불이 승인되면 ORDER_STATE_BAD_REQUEST, 상태가 바뀌지 않고 이벤트를 발행하지 않는다")
    void acceptRefund_notRefundRequested() {
        Order order = order(SHIPPED);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderRefundResultUseCase.acceptRefund(accepted))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);

        assertThat(order.getState()).isEqualTo(SHIPPED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("환불이 거절되면 환불 요청 상태의 주문이 CONFIRMED가 되고, 구매 확정 이벤트를 발행한다")
    void rejectRefund_confirmsAndPublishes() {
        Order order = order(REFUND_REQUESTED);
        when(orderSupport.getOrderWithParticipants(100L)).thenReturn(order);

        LocalDateTime before = LocalDateTime.now();
        orderRefundResultUseCase.rejectRefund(rejected);
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
    @DisplayName("환불 요청 상태가 아닌 주문의 환불이 거절되면 ORDER_STATE_BAD_REQUEST, 상태가 바뀌지 않고 이벤트를 발행하지 않는다")
    void rejectRefund_notRefundRequested() {
        Order order = order(REFUNDED);
        when(orderSupport.getOrderWithParticipants(100L)).thenReturn(order);

        assertThatThrownBy(() -> orderRefundResultUseCase.rejectRefund(rejected))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);

        assertThat(order.getState()).isEqualTo(REFUNDED);
        verifyNoInteractions(eventPublisher);
    }

    private Order order(OrderState state) {
        Order order = new Order(new OrderGroup(33_000L), winner, seller, 10L, 20L, 30_000L, 3_000L);
        order.updateState(state);
        return order;
    }
}
