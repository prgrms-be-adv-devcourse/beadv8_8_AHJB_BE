package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.OrderGroupRepository;
import com.jjinmak.back.boundedContext.order.out.OrderRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
import com.jjinmak.back.shared.cart.event.CartCreateOrderEvent;
import com.jjinmak.back.shared.order.event.OrderCreatePaymentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.WAITING;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_BAD_REQUEST;
import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentUseCaseTest {

    @Mock
    OrderSupport orderSupport;

    @Mock
    OrderRepository orderRepository;

    @Mock
    OrderGroupRepository orderGroupRepository;

    @Mock
    EventPublisher eventPublisher;

    @InjectMocks
    OrderPaymentUseCase orderPaymentUseCase;

    OrderMember winner;
    OrderMember seller;
    OrderMember otherSeller;

    LocalDateTime winAt = LocalDateTime.of(2026, 10, 15, 12, 0);

    @BeforeEach
    void setUp() {
        winner = new OrderMember(1L, UUID.randomUUID(), "낙찰자");
        seller = new OrderMember(2L, UUID.randomUUID(), "판매자");
        otherSeller = new OrderMember(4L, UUID.randomUUID(), "다른 판매자");
    }

    @Test
    @DisplayName("주문 요청의 항목마다 낙찰자, 판매자, 금액을 담은 WAITING 주문을 만들어 총액을 가진 하나의 주문 그룹에 저장한다")
    void tryPayment_savesGroupAndOrders() {
        CartItemDto item1 = cartItem(winner, seller, 10L, 20L, 30_000L, 3_000L);
        CartItemDto item2 = cartItem(winner, otherSeller, 11L, 21L, 50_000L, 4_000L);
        when(orderSupport.getMember(winner.getUuid())).thenReturn(winner);
        when(orderSupport.getMember(seller.getUuid())).thenReturn(seller);
        when(orderSupport.getMember(otherSeller.getUuid())).thenReturn(otherSeller);
        when(orderGroupRepository.save(any(OrderGroup.class))).thenAnswer(savedWithId(50L));
        when(orderRepository.save(any(Order.class))).thenAnswer(savedWithId(100L)).thenAnswer(savedWithId(101L));

        orderPaymentUseCase.tryPayment(new CartCreateOrderEvent(87_000L, List.of(item1, item2)));

        ArgumentCaptor<OrderGroup> groupCaptor = ArgumentCaptor.forClass(OrderGroup.class);
        verify(orderGroupRepository).save(groupCaptor.capture());
        OrderGroup group = groupCaptor.getValue();
        assertThat(group.getTotalPrice()).isEqualTo(87_000L);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(2)).save(orderCaptor.capture());
        List<Order> orders = orderCaptor.getAllValues();

        Order first = orders.get(0);
        assertThat(first.getGroup()).isSameAs(group);
        assertThat(first.getWinner()).isSameAs(winner);
        assertThat(first.getSeller()).isSameAs(seller);
        assertThat(first.getAuctionId()).isEqualTo(10L);
        assertThat(first.getProductId()).isEqualTo(20L);
        assertThat(first.getWinningPrice()).isEqualTo(30_000L);
        assertThat(first.getDeliveryFee()).isEqualTo(3_000L);
        assertThat(first.getState()).isEqualTo(WAITING);

        Order second = orders.get(1);
        assertThat(second.getGroup()).isSameAs(group);
        assertThat(second.getWinner()).isSameAs(winner);
        assertThat(second.getSeller()).isSameAs(otherSeller);
        assertThat(second.getAuctionId()).isEqualTo(11L);
        assertThat(second.getProductId()).isEqualTo(21L);
        assertThat(second.getWinningPrice()).isEqualTo(50_000L);
        assertThat(second.getDeliveryFee()).isEqualTo(4_000L);
        assertThat(second.getState()).isEqualTo(WAITING);

        assertThat(group.getOrders()).containsExactly(first, second);
    }

    @Test
    @DisplayName("주문을 저장하면 주문 그룹 id, 낙찰자, 항목별 주문 id와 판매자 id, 총액을 담은 결제 요청 이벤트를 한 번 발행한다")
    void tryPayment_publishesPaymentEvent() {
        CartItemDto item1 = cartItem(winner, seller, 10L, 20L, 30_000L, 3_000L);
        CartItemDto item2 = cartItem(winner, otherSeller, 11L, 21L, 50_000L, 4_000L);
        when(orderSupport.getMember(winner.getUuid())).thenReturn(winner);
        when(orderSupport.getMember(seller.getUuid())).thenReturn(seller);
        when(orderSupport.getMember(otherSeller.getUuid())).thenReturn(otherSeller);
        when(orderGroupRepository.save(any(OrderGroup.class))).thenAnswer(savedWithId(50L));
        when(orderRepository.save(any(Order.class))).thenAnswer(savedWithId(100L)).thenAnswer(savedWithId(101L));

        orderPaymentUseCase.tryPayment(new CartCreateOrderEvent(87_000L, List.of(item1, item2)));

        ArgumentCaptor<OrderCreatePaymentEvent> captor = ArgumentCaptor.forClass(OrderCreatePaymentEvent.class);
        verify(eventPublisher).publish(captor.capture());

        OrderCreatePaymentEvent event = captor.getValue();
        assertThat(event.orderGroupId()).isEqualTo(50L);
        assertThat(event.winner()).isEqualTo(new OrderCreatePaymentEvent.Member(1L, winner.getUuid()));
        assertThat(event.orderItems()).containsExactly(
                new OrderCreatePaymentEvent.OrderItem(100L, 2L, 30_000L, 3_000L),
                new OrderCreatePaymentEvent.OrderItem(101L, 4L, 50_000L, 4_000L)
        );
        assertThat(event.totalPrice()).isEqualTo(87_000L);
    }

    @Test
    @DisplayName("주문할 항목이 없으면 ORDER_BAD_REQUEST, 회원을 조회하지 않고 아무것도 저장하지 않으며 이벤트를 발행하지 않는다")
    void tryPayment_emptyItems() {
        CartCreateOrderEvent event = new CartCreateOrderEvent(0L, List.of());

        assertThatThrownBy(() -> orderPaymentUseCase.tryPayment(event))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_BAD_REQUEST);

        verifyNoInteractions(orderSupport, orderRepository, orderGroupRepository, eventPublisher);
    }

    @Test
    @DisplayName("낙찰자가 서로 다른 항목이 섞여 있으면 ORDER_BAD_REQUEST, 아무것도 저장하지 않고 이벤트를 발행하지 않는다")
    void tryPayment_differentWinners() {
        OrderMember otherWinner = new OrderMember(3L, UUID.randomUUID(), "다른 회원");
        CartItemDto item1 = cartItem(winner, seller, 10L, 20L, 30_000L, 3_000L);
        CartItemDto item2 = cartItem(otherWinner, seller, 11L, 21L, 50_000L, 4_000L);
        CartCreateOrderEvent event = new CartCreateOrderEvent(87_000L, List.of(item1, item2));

        assertThatThrownBy(() -> orderPaymentUseCase.tryPayment(event))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_BAD_REQUEST);

        verifyNoInteractions(orderSupport, orderRepository, orderGroupRepository, eventPublisher);
    }

    @Test
    @DisplayName("낙찰자가 주문 회원으로 동기화되지 않았으면 USER_NOT_FOUND, 주문 그룹과 주문을 저장하지 않고 이벤트를 발행하지 않는다")
    void tryPayment_winnerNotSynced() {
        CartItemDto item = cartItem(winner, seller, 10L, 20L, 30_000L, 3_000L);
        when(orderSupport.getMember(winner.getUuid())).thenThrow(new BusinessException(USER_NOT_FOUND));

        assertThatThrownBy(() -> orderPaymentUseCase.tryPayment(new CartCreateOrderEvent(33_000L, List.of(item))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        verifyNoInteractions(orderGroupRepository, orderRepository, eventPublisher);
    }

    @Test
    @DisplayName("판매자 중 한 명이라도 주문 회원으로 동기화되지 않았으면 USER_NOT_FOUND, 결제 요청 이벤트를 발행하지 않는다")
    void tryPayment_sellerNotSynced() {
        CartItemDto item1 = cartItem(winner, seller, 10L, 20L, 30_000L, 3_000L);
        CartItemDto item2 = cartItem(winner, otherSeller, 11L, 21L, 50_000L, 4_000L);
        when(orderSupport.getMember(winner.getUuid())).thenReturn(winner);
        when(orderSupport.getMember(seller.getUuid())).thenReturn(seller);
        when(orderSupport.getMember(otherSeller.getUuid())).thenThrow(new BusinessException(USER_NOT_FOUND));
        when(orderGroupRepository.save(any(OrderGroup.class))).thenAnswer(savedWithId(50L));
        when(orderRepository.save(any(Order.class))).thenAnswer(savedWithId(100L));

        assertThatThrownBy(() -> orderPaymentUseCase.tryPayment(new CartCreateOrderEvent(87_000L, List.of(item1, item2))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        verifyNoInteractions(eventPublisher);
    }

    private CartItemDto cartItem(OrderMember winner, OrderMember seller, Long auctionId, Long productId, Long winningPrice, Long deliveryFee) {
        return new CartItemDto(null, winner.getUuid(), seller.getUuid(), auctionId, productId, winningPrice, deliveryFee, winAt, winAt.plusDays(1));
    }

    private Answer<Object> savedWithId(Long id) {
        return invocation -> {
            Object entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", id);
            return entity;
        };
    }
}
