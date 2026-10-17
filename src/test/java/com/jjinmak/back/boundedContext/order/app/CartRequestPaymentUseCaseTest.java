package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.CartItem;
import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.repository.CartItemRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderGroupRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderRepository;
import com.jjinmak.back.global.exception.BusinessException;
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
import java.util.Set;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.WAITING;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.CART_ITEM_FORBIDDEN;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.CART_ITEM_PAYMENT_EXPIRED;
import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartRequestPaymentUseCaseTest {

    @Mock
    OrderSupport orderSupport;

    @Mock
    CartItemRepository cartItemRepository;

    @Mock
    OrderRepository orderRepository;

    @Mock
    OrderGroupRepository orderGroupRepository;

    @InjectMocks
    CartRequestPaymentUseCase cartRequestPaymentUseCase;

    OrderMember winner;
    OrderMember seller;
    OrderMember otherSeller;

    @BeforeEach
    void setUp() {
        winner = new OrderMember(1L, UUID.randomUUID(), "낙찰자");
        seller = new OrderMember(2L, UUID.randomUUID(), "판매자");
        otherSeller = new OrderMember(4L, UUID.randomUUID(), "다른 판매자");
    }

    @Test
    @DisplayName("결제 요청에 성공하면 항목마다 낙찰자, 판매자, 금액을 담은 WAITING 주문을 만들어 총액을 가진 하나의 주문 그룹에 저장한다")
    void requestPayment_savesGroupAndOrders() {
        CartItem item1 = cartItem(seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now());
        CartItem item2 = cartItem(otherSeller, 11L, 21L, 50_000L, 4_000L, LocalDateTime.now());
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L, 21L)))
                .thenReturn(List.of(item1, item2));
        when(orderGroupRepository.save(any(OrderGroup.class))).thenAnswer(savedWithId(50L));
        when(orderRepository.save(any(Order.class))).thenAnswer(savedWithId(100L)).thenAnswer(savedWithId(101L));

        cartRequestPaymentUseCase.requestPayment(1L, List.of(20L, 21L));

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
    @DisplayName("같은 상품 id를 중복으로 요청해도 한 번만 주문한다")
    void requestPayment_duplicateProductIds() {
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L)))
                .thenReturn(List.of(cartItem(seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now())));
        when(orderGroupRepository.save(any(OrderGroup.class))).thenAnswer(savedWithId(50L));
        when(orderRepository.save(any(Order.class))).thenAnswer(savedWithId(100L));

        cartRequestPaymentUseCase.requestPayment(1L, List.of(20L, 20L));

        ArgumentCaptor<OrderGroup> groupCaptor = ArgumentCaptor.forClass(OrderGroup.class);
        verify(orderGroupRepository).save(groupCaptor.capture());
        assertThat(groupCaptor.getValue().getTotalPrice()).isEqualTo(33_000L);
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("요청한 상품 중 내 장바구니에 없는 것이 있으면 CART_ITEM_FORBIDDEN, 주문 그룹과 주문을 저장하지 않는다")
    void requestPayment_itemNotInMyCart() {
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L, 99L)))
                .thenReturn(List.of(cartItem(seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now())));

        assertThatThrownBy(() -> cartRequestPaymentUseCase.requestPayment(1L, List.of(20L, 99L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CART_ITEM_FORBIDDEN);

        verifyNoInteractions(orderGroupRepository, orderRepository);
    }

    @Test
    @DisplayName("결제 기한이 지난 항목이 섞여 있으면 CART_ITEM_PAYMENT_EXPIRED, 주문 그룹과 주문을 하나도 저장하지 않는다")
    void requestPayment_expiredItemMixed() {
        LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L, 21L)))
                .thenReturn(List.of(
                        cartItem(seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now()),
                        cartItem(otherSeller, 11L, 21L, 50_000L, 4_000L, twoDaysAgo)));

        assertThatThrownBy(() -> cartRequestPaymentUseCase.requestPayment(1L, List.of(20L, 21L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CART_ITEM_PAYMENT_EXPIRED);

        verifyNoInteractions(orderGroupRepository, orderRepository);
    }

    @Test
    @DisplayName("결제 기한 직전이면 주문을 만든다")
    void requestPayment_justBeforeDue() {
        LocalDateTime winAt = LocalDateTime.now().minusDays(1).plusMinutes(1);
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L)))
                .thenReturn(List.of(cartItem(seller, 10L, 20L, 30_000L, 3_000L, winAt)));
        when(orderGroupRepository.save(any(OrderGroup.class))).thenAnswer(savedWithId(50L));
        when(orderRepository.save(any(Order.class))).thenAnswer(savedWithId(100L));

        cartRequestPaymentUseCase.requestPayment(1L, List.of(20L));

        verify(orderGroupRepository).save(any(OrderGroup.class));
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 회원이 없으면 USER_NOT_FOUND, 장바구니를 조회하지 않고 아무것도 저장하지 않는다")
    void requestPayment_memberNotFound() {
        when(orderSupport.getMember(1L)).thenThrow(new BusinessException(USER_NOT_FOUND));

        assertThatThrownBy(() -> cartRequestPaymentUseCase.requestPayment(1L, List.of(20L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        verifyNoInteractions(cartItemRepository, orderGroupRepository, orderRepository);
    }

    private CartItem cartItem(OrderMember seller, Long auctionId, Long productId, Long winningPrice, Long deliveryFee, LocalDateTime winAt) {
        return new CartItem(winner, seller, auctionId, productId, winningPrice, deliveryFee, winAt);
    }

    private Answer<Object> savedWithId(Long id) {
        return invocation -> {
            Object entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", id);
            return entity;
        };
    }
}
