package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.app.dto.PaymentResponseDto;
import com.jjinmak.back.boundedContext.order.domain.CartItem;
import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.payment.PaymentRequest;
import com.jjinmak.back.boundedContext.order.out.repository.CartItemRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderGroupRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.order.event.OrderPaymentSucceedEvent;
import com.jjinmak.back.shared.payment.dto.PaymentRequestDto;
import com.jjinmak.back.shared.payment.dto.PaymentResultDto;
import com.jjinmak.back.shared.payment.event.PaymentPaidEvent;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.PAID;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.WAITING;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.CART_ITEM_FORBIDDEN;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.CART_ITEM_NOT_FOUND;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.CART_ITEM_PAYMENT_EXPIRED;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_GROUP_FORBIDDEN;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_GROUP_NOT_FOUND;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_STATE_BAD_REQUEST;
import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartRequestPaymentUseCaseTest {

    @Mock
    EventPublisher eventPublisher;

    @Mock
    PaymentRequest paymentRequest;

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

    LocalDateTime paidAt = LocalDateTime.of(2026, 10, 19, 12, 0);

    @BeforeEach
    void setUp() {
        winner = new OrderMember(1L, UUID.randomUUID(), "낙찰자");
        seller = new OrderMember(2L, UUID.randomUUID(), "판매자");
        otherSeller = new OrderMember(4L, UUID.randomUUID(), "다른 판매자");
    }

    @Test
    @DisplayName("주문 생성에 성공하면 항목마다 낙찰자, 판매자, 금액을 담은 WAITING 주문을 만들어 총액을 가진 하나의 주문 그룹에 저장하고, 결제는 요청하지 않는다")
    void createOrder_savesGroupAndOrders() {
        CartItem item1 = cartItem(seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now());
        CartItem item2 = cartItem(otherSeller, 11L, 21L, 50_000L, 4_000L, LocalDateTime.now());
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L, 21L)))
                .thenReturn(List.of(item1, item2));
        when(orderGroupRepository.save(any(OrderGroup.class))).thenAnswer(savedWithId(50L));
        when(orderRepository.save(any(Order.class))).thenAnswer(savedWithId(100L)).thenAnswer(savedWithId(101L));

        cartRequestPaymentUseCase.createOrder(1L, List.of(20L, 21L));

        ArgumentCaptor<OrderGroup> groupCaptor = ArgumentCaptor.forClass(OrderGroup.class);
        verify(orderGroupRepository).save(groupCaptor.capture());
        OrderGroup group = groupCaptor.getValue();
        assertThat(group.getWinner()).isSameAs(winner);
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
        verifyNoInteractions(paymentRequest);
    }

    @Test
    @DisplayName("주문 생성 결과로 주문 그룹 id, 낙찰자 id, 총액과 함께 저장된 주문 id, 판매자 id, 금액을 담은 결제 요청 정보를 돌려준다")
    void createOrder_returnsPaymentRequestWithSavedOrderIds() {
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L, 21L)))
                .thenReturn(List.of(
                        cartItem(seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now()),
                        cartItem(otherSeller, 11L, 21L, 50_000L, 4_000L, LocalDateTime.now())));
        when(orderGroupRepository.save(any(OrderGroup.class))).thenAnswer(savedWithId(50L));
        when(orderRepository.save(any(Order.class))).thenAnswer(savedWithId(100L)).thenAnswer(savedWithId(101L));

        PaymentRequestDto request = cartRequestPaymentUseCase.createOrder(1L, List.of(20L, 21L));

        assertThat(request).isEqualTo(new PaymentRequestDto(50L, 1L, 87_000L, List.of(
                new PaymentRequestDto.Item(100L, 2L, 30_000L, 3_000L),
                new PaymentRequestDto.Item(101L, 4L, 50_000L, 4_000L))));
    }

    @Test
    @DisplayName("같은 상품 id를 중복으로 요청해도 한 번만 주문한다")
    void createOrder_duplicateProductIds() {
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L)))
                .thenReturn(List.of(cartItem(seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now())));
        when(orderGroupRepository.save(any(OrderGroup.class))).thenAnswer(savedWithId(50L));
        when(orderRepository.save(any(Order.class))).thenAnswer(savedWithId(100L));

        PaymentRequestDto request = cartRequestPaymentUseCase.createOrder(1L, List.of(20L, 20L));

        ArgumentCaptor<OrderGroup> groupCaptor = ArgumentCaptor.forClass(OrderGroup.class);
        verify(orderGroupRepository).save(groupCaptor.capture());
        assertThat(groupCaptor.getValue().getTotalPrice()).isEqualTo(33_000L);
        verify(orderRepository, times(1)).save(any(Order.class));
        assertThat(request.items()).hasSize(1);
    }

    @Test
    @DisplayName("요청한 상품 중 내 장바구니에 없는 것이 있으면 CART_ITEM_FORBIDDEN, 주문 그룹과 주문을 저장하지 않는다")
    void createOrder_itemNotInMyCart() {
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L, 99L)))
                .thenReturn(List.of(cartItem(seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now())));

        assertThatThrownBy(() -> cartRequestPaymentUseCase.createOrder(1L, List.of(20L, 99L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CART_ITEM_FORBIDDEN);

        verifyNoInteractions(orderGroupRepository, orderRepository);
    }

    @Test
    @DisplayName("결제 기한이 지난 항목이 섞여 있으면 CART_ITEM_PAYMENT_EXPIRED, 주문 그룹과 주문을 하나도 저장하지 않는다")
    void createOrder_expiredItemMixed() {
        LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L, 21L)))
                .thenReturn(List.of(
                        cartItem(seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now()),
                        cartItem(otherSeller, 11L, 21L, 50_000L, 4_000L, twoDaysAgo)));

        assertThatThrownBy(() -> cartRequestPaymentUseCase.createOrder(1L, List.of(20L, 21L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CART_ITEM_PAYMENT_EXPIRED);

        verifyNoInteractions(orderGroupRepository, orderRepository);
    }

    @Test
    @DisplayName("결제 기한 직전이면 주문을 만든다")
    void createOrder_justBeforeDue() {
        LocalDateTime winAt = LocalDateTime.now().minusDays(1).plusMinutes(1);
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L)))
                .thenReturn(List.of(cartItem(seller, 10L, 20L, 30_000L, 3_000L, winAt)));
        when(orderGroupRepository.save(any(OrderGroup.class))).thenAnswer(savedWithId(50L));
        when(orderRepository.save(any(Order.class))).thenAnswer(savedWithId(100L));

        cartRequestPaymentUseCase.createOrder(1L, List.of(20L));

        verify(orderGroupRepository).save(any(OrderGroup.class));
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 회원이 없으면 USER_NOT_FOUND, 장바구니를 조회하지 않고 아무것도 저장하지 않는다")
    void createOrder_memberNotFound() {
        when(orderSupport.getMember(1L)).thenThrow(new BusinessException(USER_NOT_FOUND));

        assertThatThrownBy(() -> cartRequestPaymentUseCase.createOrder(1L, List.of(20L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        verifyNoInteractions(cartItemRepository, orderGroupRepository, orderRepository);
    }

    @Test
    @DisplayName("재사용할 주문 그룹은 낙찰자와 지금 시각으로, lifeTime이 남은 그룹 중에서 찾는다")
    void createOrder_findsReusableGroupsWithWinnerAndNow() {
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L)))
                .thenReturn(List.of(cartItem(seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now())));
        when(orderGroupRepository.save(any(OrderGroup.class))).thenAnswer(savedWithId(50L));
        when(orderRepository.save(any(Order.class))).thenAnswer(savedWithId(100L));

        LocalDateTime before = LocalDateTime.now();
        cartRequestPaymentUseCase.createOrder(1L, List.of(20L));
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderGroupRepository).findAllByWinnerAndLifeTime(eq(winner), captor.capture());
        assertThat(captor.getValue()).isBetween(before, after);
    }

    @Test
    @DisplayName("lifeTime이 남은 WAITING 그룹이 요청 상품과 같은 조합이면(요청 순서는 달라도) 주문 그룹과 주문을 새로 저장하지 않고, 그 그룹 id와 기존 주문으로 결제 요청 정보를 돌려준다")
    void createOrder_reusesWaitingGroupWithSameProducts() {
        OrderGroup existing = group(50L, 87_000L);
        order(existing, 100L, seller, 10L, 20L, 30_000L, 3_000L);
        order(existing, 101L, otherSeller, 11L, 21L, 50_000L, 4_000L);
        stubCartWithTwoItems();
        when(orderGroupRepository.findAllByWinnerAndLifeTime(eq(winner), any(LocalDateTime.class))).thenReturn(List.of(existing));

        PaymentRequestDto request = cartRequestPaymentUseCase.createOrder(1L, List.of(21L, 20L));

        assertThat(request).isEqualTo(new PaymentRequestDto(50L, 1L, 87_000L, List.of(
                new PaymentRequestDto.Item(100L, 2L, 30_000L, 3_000L),
                new PaymentRequestDto.Item(101L, 4L, 50_000L, 4_000L))));
        verify(orderGroupRepository, never()).save(any(OrderGroup.class));
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("주문 그룹을 재사용하면 그 그룹의 lifeTime을 재사용한 시각부터 1시간 뒤로 늘린다")
    void createOrder_reuseExtendsLifeTime() {
        OrderGroup existing = group(50L, 33_000L);
        ReflectionTestUtils.setField(existing, "lifeTime", LocalDateTime.now().plusMinutes(1));
        order(existing, 100L, seller, 10L, 20L, 30_000L, 3_000L);
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L)))
                .thenReturn(List.of(cartItem(seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now())));
        when(orderGroupRepository.findAllByWinnerAndLifeTime(eq(winner), any(LocalDateTime.class))).thenReturn(List.of(existing));

        LocalDateTime before = LocalDateTime.now();
        cartRequestPaymentUseCase.createOrder(1L, List.of(20L));
        LocalDateTime after = LocalDateTime.now();

        assertThat(existing.getLifeTime()).isBetween(before.plusHours(1), after.plusHours(1));
    }

    @Test
    @DisplayName("같은 조합이어도 WAITING이 아닌(결제가 끝난) 그룹은 건너뛰고, 그 뒤에 있는 같은 조합의 WAITING 그룹을 재사용한다")
    void createOrder_skipsNonWaitingGroup() {
        OrderGroup paidGroup = group(49L, 33_000L);
        order(paidGroup, 90L, seller, 10L, 20L, 30_000L, 3_000L).updateState(PAID);
        OrderGroup waitingGroup = group(50L, 33_000L);
        order(waitingGroup, 100L, seller, 10L, 20L, 30_000L, 3_000L);
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L)))
                .thenReturn(List.of(cartItem(seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now())));
        when(orderGroupRepository.findAllByWinnerAndLifeTime(eq(winner), any(LocalDateTime.class))).thenReturn(List.of(paidGroup, waitingGroup));

        PaymentRequestDto request = cartRequestPaymentUseCase.createOrder(1L, List.of(20L));

        assertThat(request.orderGroupId()).isEqualTo(50L);
        assertThat(request.items()).extracting(PaymentRequestDto.Item::orderId).containsExactly(100L);
        verify(orderGroupRepository, never()).save(any(OrderGroup.class));
    }

    @Test
    @DisplayName("요청 상품이 기존 WAITING 그룹 상품의 일부뿐이면(작은 조합) 재사용하지 않고 새 주문 그룹을 만든다")
    void createOrder_subsetOfGroupMakesNewGroup() {
        OrderGroup existing = group(50L, 87_000L);
        order(existing, 100L, seller, 10L, 20L, 30_000L, 3_000L);
        order(existing, 101L, otherSeller, 11L, 21L, 50_000L, 4_000L);
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L)))
                .thenReturn(List.of(cartItem(seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now())));
        when(orderGroupRepository.findAllByWinnerAndLifeTime(eq(winner), any(LocalDateTime.class))).thenReturn(List.of(existing));
        when(orderGroupRepository.save(any(OrderGroup.class))).thenAnswer(savedWithId(51L));
        when(orderRepository.save(any(Order.class))).thenAnswer(savedWithId(102L));

        PaymentRequestDto request = cartRequestPaymentUseCase.createOrder(1L, List.of(20L));

        assertThat(request).isEqualTo(new PaymentRequestDto(51L, 1L, 33_000L, List.of(
                new PaymentRequestDto.Item(102L, 2L, 30_000L, 3_000L))));
    }

    @Test
    @DisplayName("요청 상품이 기존 WAITING 그룹 상품을 모두 포함하고 더 많으면(큰 조합) 재사용하지 않고 새 주문 그룹을 만든다")
    void createOrder_supersetOfGroupMakesNewGroup() {
        OrderGroup existing = group(50L, 33_000L);
        order(existing, 100L, seller, 10L, 20L, 30_000L, 3_000L);
        stubCartWithTwoItems();
        when(orderGroupRepository.findAllByWinnerAndLifeTime(eq(winner), any(LocalDateTime.class))).thenReturn(List.of(existing));
        when(orderGroupRepository.save(any(OrderGroup.class))).thenAnswer(savedWithId(51L));
        when(orderRepository.save(any(Order.class))).thenAnswer(savedWithId(102L)).thenAnswer(savedWithId(103L));

        PaymentRequestDto request = cartRequestPaymentUseCase.createOrder(1L, List.of(20L, 21L));

        assertThat(request).isEqualTo(new PaymentRequestDto(51L, 1L, 87_000L, List.of(
                new PaymentRequestDto.Item(102L, 2L, 30_000L, 3_000L),
                new PaymentRequestDto.Item(103L, 4L, 50_000L, 4_000L))));
    }

    @Test
    @DisplayName("상품 개수가 같아도 상품이 다른 WAITING 그룹은 재사용하지 않고 새 주문 그룹을 만든다")
    void createOrder_sameSizeDifferentProductsMakesNewGroup() {
        OrderGroup existing = group(50L, 63_000L);
        order(existing, 100L, seller, 10L, 20L, 30_000L, 3_000L);
        order(existing, 101L, otherSeller, 12L, 22L, 27_000L, 3_000L);
        stubCartWithTwoItems();
        when(orderGroupRepository.findAllByWinnerAndLifeTime(eq(winner), any(LocalDateTime.class))).thenReturn(List.of(existing));
        when(orderGroupRepository.save(any(OrderGroup.class))).thenAnswer(savedWithId(51L));
        when(orderRepository.save(any(Order.class))).thenAnswer(savedWithId(102L)).thenAnswer(savedWithId(103L));

        PaymentRequestDto request = cartRequestPaymentUseCase.createOrder(1L, List.of(20L, 21L));

        assertThat(request.orderGroupId()).isEqualTo(51L);
        verify(orderRepository, times(2)).save(any(Order.class));
    }

    @Test
    @DisplayName("결제 요청은 결제 요청 정보를 그대로 결제 컨텍스트에 넘기고, 받은 결과를 그대로 돌려준다")
    void requestPayment_delegatesToPaymentRequest() {
        PaymentRequestDto request = new PaymentRequestDto(50L, 1L, 33_000L,
                List.of(new PaymentRequestDto.Item(100L, 2L, 30_000L, 3_000L)));
        PaymentResultDto result = PaymentResultDto.paid(7L, 33_000L);
        when(paymentRequest.requestPayment(request)).thenReturn(result);

        PaymentResultDto returned = cartRequestPaymentUseCase.requestPayment(request);

        assertThat(returned).isSameAs(result);
        verifyNoInteractions(orderGroupRepository, orderRepository, cartItemRepository, eventPublisher);
    }

    @Test
    @DisplayName("결제 결과가 PAID면 isPaid=true로 응답만 하고, 주문 그룹을 조회하거나 장바구니를 지우거나 이벤트를 발행하지 않는다")
    void handlePaymentResult_paid() {
        PaymentResponseDto response = cartRequestPaymentUseCase.handlePaymentResult(50L, PaymentResultDto.paid(7L, 87_000L));

        assertThat(response).isEqualTo(new PaymentResponseDto(50L, true, null));
        verifyNoInteractions(orderGroupRepository, orderRepository, cartItemRepository, eventPublisher);
    }

    @Test
    @DisplayName("결제 완료 이벤트를 받으면 그룹의 모든 주문을 이벤트의 결제 시각으로 PAID 처리하고, 주문마다 장바구니 항목을 지우고 같은 결제 시각으로 결제 성공 이벤트를 발행한다")
    void paymentPaid_completesEveryOrder() {
        OrderGroup group = new OrderGroup(winner, 87_000L);
        Order first = order(group, 100L, seller, 10L, 20L, 30_000L, 3_000L);
        Order second = order(group, 101L, otherSeller, 11L, 21L, 50_000L, 4_000L);
        CartItem firstItem = cartItem(seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now());
        CartItem secondItem = cartItem(otherSeller, 11L, 21L, 50_000L, 4_000L, LocalDateTime.now());
        when(orderGroupRepository.findByIdWithOrders(50L)).thenReturn(Optional.of(group));
        when(cartItemRepository.findByAuctionId(10L)).thenReturn(Optional.of(firstItem));
        when(cartItemRepository.findByAuctionId(11L)).thenReturn(Optional.of(secondItem));

        cartRequestPaymentUseCase.paymentPaid(paidEvent(50L, 1L));

        assertThat(first.getState()).isEqualTo(PAID);
        assertThat(first.getPaidAt()).isEqualTo(paidAt);
        assertThat(second.getState()).isEqualTo(PAID);
        assertThat(second.getPaidAt()).isEqualTo(paidAt);
        verify(cartItemRepository).delete(firstItem);
        verify(cartItemRepository).delete(secondItem);

        ArgumentCaptor<OrderPaymentSucceedEvent> captor = ArgumentCaptor.forClass(OrderPaymentSucceedEvent.class);
        verify(eventPublisher, times(2)).publish(captor.capture());
        assertThat(captor.getAllValues()).containsExactly(
                new OrderPaymentSucceedEvent(100L, 10L, 20L, paidAt),
                new OrderPaymentSucceedEvent(101L, 11L, 21L, paidAt));
    }

    @Test
    @DisplayName("결제 완료 이벤트의 주문 그룹이 없으면 ORDER_GROUP_NOT_FOUND, 장바구니를 지우지 않고 이벤트를 발행하지 않는다")
    void paymentPaid_groupNotFound() {
        when(orderGroupRepository.findByIdWithOrders(50L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartRequestPaymentUseCase.paymentPaid(paidEvent(50L, 1L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_GROUP_NOT_FOUND);

        verifyNoInteractions(cartItemRepository, eventPublisher);
    }

    @Test
    @DisplayName("결제 완료 이벤트의 구매자가 주문의 낙찰자가 아니면(판매자 id로 와도) ORDER_GROUP_FORBIDDEN, 주문은 WAITING 그대로이고 장바구니를 지우지 않고 이벤트를 발행하지 않는다")
    void paymentPaid_buyerIsNotWinner() {
        OrderGroup group = new OrderGroup(winner, 33_000L);
        Order order = order(group, 100L, seller, 10L, 20L, 30_000L, 3_000L);
        when(orderGroupRepository.findByIdWithOrders(50L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> cartRequestPaymentUseCase.paymentPaid(paidEvent(50L, 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_GROUP_FORBIDDEN);

        assertThat(order.getState()).isEqualTo(WAITING);
        assertThat(order.getPaidAt()).isNull();
        verifyNoInteractions(cartItemRepository, eventPublisher);
    }

    @Test
    @DisplayName("결제 완료 이벤트를 받았는데 이미 결제된 주문이 있으면 ORDER_STATE_BAD_REQUEST, 장바구니를 지우지 않고 이벤트를 발행하지 않는다")
    void paymentPaid_orderNotWaiting() {
        OrderGroup group = new OrderGroup(winner, 33_000L);
        Order order = order(group, 100L, seller, 10L, 20L, 30_000L, 3_000L);
        order.updateState(PAID);
        when(orderGroupRepository.findByIdWithOrders(50L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> cartRequestPaymentUseCase.paymentPaid(paidEvent(50L, 1L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);

        verifyNoInteractions(cartItemRepository, eventPublisher);
    }

    @Test
    @DisplayName("결제 완료 이벤트를 받았는데 주문의 장바구니 항목이 없으면 CART_ITEM_NOT_FOUND, 이벤트를 발행하지 않는다")
    void paymentPaid_cartItemNotFound() {
        OrderGroup group = new OrderGroup(winner, 33_000L);
        order(group, 100L, seller, 10L, 20L, 30_000L, 3_000L);
        when(orderGroupRepository.findByIdWithOrders(50L)).thenReturn(Optional.of(group));
        when(cartItemRepository.findByAuctionId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartRequestPaymentUseCase.paymentPaid(paidEvent(50L, 1L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CART_ITEM_NOT_FOUND);

        verify(cartItemRepository, never()).delete(any(CartItem.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("결제 결과가 NEEDS_CHARGE면 주문과 장바구니를 그대로 두고, isPaid=false와 충전에 필요한 결제 정보를 돌려준다")
    void handlePaymentResult_needsCharge() {
        PaymentResultDto result = PaymentResultDto.needsCharge(7L, 87_000L, 37_000L, "CHG-1", "찐막 주문 결제 (부족분 충전)");

        PaymentResponseDto response = cartRequestPaymentUseCase.handlePaymentResult(50L, result);

        assertThat(response).isEqualTo(new PaymentResponseDto(50L, false,
                new PaymentResponseDto.Data(7L, 87_000L, 37_000L, "CHG-1", "찐막 주문 결제 (부족분 충전)")));
        verifyNoInteractions(orderGroupRepository, cartItemRepository, eventPublisher);
    }

    private PaymentPaidEvent paidEvent(Long orderGroupId, Long buyerId) {
        return PaymentPaidEvent.of(orderGroupId, 7L, buyerId, 87_000L, paidAt);
    }

    private void stubCartWithTwoItems() {
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L, 21L)))
                .thenReturn(List.of(
                        cartItem(seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now()),
                        cartItem(otherSeller, 11L, 21L, 50_000L, 4_000L, LocalDateTime.now())));
    }

    private CartItem cartItem(OrderMember seller, Long auctionId, Long productId, Long winningPrice, Long deliveryFee, LocalDateTime winAt) {
        return new CartItem(winner, seller, auctionId, productId, winningPrice, deliveryFee, winAt);
    }

    private OrderGroup group(Long id, Long totalPrice) {
        OrderGroup group = new OrderGroup(winner, totalPrice);
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }

    private Order order(OrderGroup group, Long id, OrderMember seller, Long auctionId, Long productId, Long winningPrice, Long deliveryFee) {
        Order order = new Order(group, winner, seller, auctionId, productId, winningPrice, deliveryFee);
        ReflectionTestUtils.setField(order, "id", id);
        group.add(order);
        return order;
    }

    private Answer<Object> savedWithId(Long id) {
        return invocation -> {
            Object entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", id);
            return entity;
        };
    }
}
