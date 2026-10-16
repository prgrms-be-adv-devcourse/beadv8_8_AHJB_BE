package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.app.dto.OrderDto;
import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.domain.OrderState;
import com.jjinmak.back.boundedContext.order.out.OrderRepository;
import com.jjinmak.back.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.CONFIRMED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.PAID;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.REFUNDED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.REFUND_REQUESTED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPING;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_FORBIDDEN;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_NOT_FOUND;
import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderReadOrderUseCaseTest {

    @Mock
    OrderSupport orderSupport;

    @Mock
    OrderRepository orderRepository;

    @InjectMocks
    OrderReadOrderUseCase orderReadOrderUseCase;

    OrderMember winner;
    OrderMember seller;
    OrderMember other;

    LocalDateTime createdAt = LocalDateTime.of(2026, 10, 15, 9, 0);
    LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 16, 18, 30);

    @BeforeEach
    void setUp() {
        winner = new OrderMember(1L, UUID.randomUUID(), "낙찰자");
        seller = new OrderMember(2L, UUID.randomUUID(), "판매자");
        other = new OrderMember(3L, UUID.randomUUID(), "다른 회원");
    }

    @Test
    @DisplayName("낙찰자의 주문 목록을 저장소가 준 순서 그대로 DTO로 돌려준다")
    void readWinnerOrders_keepsRepositoryOrder() {
        Order first = order(101L, 11L, 21L, 50_000L, 4_000L, SHIPPED);
        Order second = order(100L, 10L, 20L, 30_000L, 3_000L, PAID);
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(orderRepository.findAllByWinnerAndStateIn(eq(winner), anySet(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        List<OrderDto> result = orderReadOrderUseCase.readWinnerOrders(1L, 0, 10);

        assertThat(result).containsExactly(
                new OrderDto(101L, winner.getUuid(), seller.getUuid(), 21L, 50_000L, 4_000L, SHIPPED, createdAt, updatedAt),
                new OrderDto(100L, winner.getUuid(), seller.getUuid(), 20L, 30_000L, 3_000L, PAID, createdAt, updatedAt)
        );
    }

    @Test
    @DisplayName("낙찰자의 주문 목록은 결제 이후 상태의 주문만 조회하고, 결제 대기·결제 실패·배송 오류 주문은 조회하지 않는다")
    void readWinnerOrders_readsPaidAndLaterStates() {
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(orderRepository.findAllByWinnerAndStateIn(eq(winner), anySet(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        orderReadOrderUseCase.readWinnerOrders(1L, 0, 10);

        verify(orderRepository).findAllByWinnerAndStateIn(winner, readStates(), PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("음수 페이지를 요청하면 첫 페이지를 조회한다")
    void readWinnerOrders_negativePage() {
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(orderRepository.findAllByWinnerAndStateIn(eq(winner), anySet(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        orderReadOrderUseCase.readWinnerOrders(1L, -1, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAllByWinnerAndStateIn(eq(winner), anySet(), captor.capture());

        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("페이지 크기가 10보다 작으면 10개씩 조회한다")
    void readWinnerOrders_sizeBelowMinimum() {
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(orderRepository.findAllByWinnerAndStateIn(eq(winner), anySet(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        orderReadOrderUseCase.readWinnerOrders(1L, 2, 5);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAllByWinnerAndStateIn(eq(winner), anySet(), captor.capture());

        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("페이지 크기가 50보다 크면 50개씩 조회한다")
    void readWinnerOrders_sizeAboveMaximum() {
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(orderRepository.findAllByWinnerAndStateIn(eq(winner), anySet(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        orderReadOrderUseCase.readWinnerOrders(1L, 2, 100);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAllByWinnerAndStateIn(eq(winner), anySet(), captor.capture());

        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("페이지와 크기가 허용 범위 안이면 요청한 값 그대로 조회한다")
    void readWinnerOrders_pageAndSizeInRange() {
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(orderRepository.findAllByWinnerAndStateIn(eq(winner), anySet(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        orderReadOrderUseCase.readWinnerOrders(1L, 3, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAllByWinnerAndStateIn(eq(winner), anySet(), captor.capture());

        assertThat(captor.getValue().getPageNumber()).isEqualTo(3);
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("주문 회원이 없으면 USER_NOT_FOUND이고, 주문은 조회하지 않는다")
    void readWinnerOrders_memberNotFound() {
        when(orderSupport.getMember(1L)).thenThrow(new BusinessException(USER_NOT_FOUND));

        assertThatThrownBy(() -> orderReadOrderUseCase.readWinnerOrders(1L, 0, 10))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("판매자의 주문 목록을 저장소가 준 순서 그대로, 주문의 낙찰자를 담아 DTO로 돌려준다")
    void readSellerOrders_keepsRepositoryOrder() {
        Order first = order(101L, 11L, 21L, 50_000L, 4_000L, REFUND_REQUESTED);
        Order second = order(100L, 10L, 20L, 30_000L, 3_000L, CONFIRMED);
        when(orderSupport.getMember(2L)).thenReturn(seller);
        when(orderRepository.findAllBySellerAndStateIn(eq(seller), anySet(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        List<OrderDto> result = orderReadOrderUseCase.readSellerOrders(2L, 0, 10);

        assertThat(result).containsExactly(
                new OrderDto(101L, winner.getUuid(), seller.getUuid(), 21L, 50_000L, 4_000L, REFUND_REQUESTED, createdAt, updatedAt),
                new OrderDto(100L, winner.getUuid(), seller.getUuid(), 20L, 30_000L, 3_000L, CONFIRMED, createdAt, updatedAt)
        );
    }

    @Test
    @DisplayName("판매자의 주문 목록도 결제 이후 상태의 주문만 조회한다")
    void readSellerOrders_readsPaidAndLaterStates() {
        when(orderSupport.getMember(2L)).thenReturn(seller);
        when(orderRepository.findAllBySellerAndStateIn(eq(seller), anySet(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        orderReadOrderUseCase.readSellerOrders(2L, 0, 10);

        verify(orderRepository).findAllBySellerAndStateIn(seller, readStates(), PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("판매자가 음수 페이지와 10보다 작은 크기를 요청하면 첫 페이지를 10개씩 조회한다")
    void readSellerOrders_negativePageAndSizeBelowMinimum() {
        when(orderSupport.getMember(2L)).thenReturn(seller);
        when(orderRepository.findAllBySellerAndStateIn(eq(seller), anySet(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        orderReadOrderUseCase.readSellerOrders(2L, -1, 5);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAllBySellerAndStateIn(eq(seller), anySet(), captor.capture());

        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("판매자가 50보다 큰 크기를 요청하면 50개씩 조회한다")
    void readSellerOrders_sizeAboveMaximum() {
        when(orderSupport.getMember(2L)).thenReturn(seller);
        when(orderRepository.findAllBySellerAndStateIn(eq(seller), anySet(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        orderReadOrderUseCase.readSellerOrders(2L, 2, 100);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAllBySellerAndStateIn(eq(seller), anySet(), captor.capture());

        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("주문 회원이 없으면 USER_NOT_FOUND이고, 주문은 조회하지 않는다")
    void readSellerOrders_memberNotFound() {
        when(orderSupport.getMember(2L)).thenThrow(new BusinessException(USER_NOT_FOUND));

        assertThatThrownBy(() -> orderReadOrderUseCase.readSellerOrders(2L, 0, 10))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("낙찰자가 주문을 조회하면 주문 DTO를 돌려준다")
    void readOrder_byWinner() {
        Order order = order(100L, 10L, 20L, 30_000L, 3_000L, SHIPPING);
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(orderRepository.findByIdAndStateInWithWinnerAndSeller(eq(100L), anySet())).thenReturn(Optional.of(order));

        OrderDto result = orderReadOrderUseCase.readOrder(1L, 100L);

        assertThat(result).isEqualTo(
                new OrderDto(100L, winner.getUuid(), seller.getUuid(), 20L, 30_000L, 3_000L, SHIPPING, createdAt, updatedAt)
        );
    }

    @Test
    @DisplayName("판매자가 주문을 조회하면 주문 DTO를 돌려준다")
    void readOrder_bySeller() {
        Order order = order(100L, 10L, 20L, 30_000L, 3_000L, REFUNDED);
        when(orderSupport.getMember(2L)).thenReturn(seller);
        when(orderRepository.findByIdAndStateInWithWinnerAndSeller(eq(100L), anySet())).thenReturn(Optional.of(order));

        OrderDto result = orderReadOrderUseCase.readOrder(2L, 100L);

        assertThat(result).isEqualTo(
                new OrderDto(100L, winner.getUuid(), seller.getUuid(), 20L, 30_000L, 3_000L, REFUNDED, createdAt, updatedAt)
        );
    }

    @Test
    @DisplayName("주문 하나를 조회할 때도 결제 이후 상태의 주문만 조회한다")
    void readOrder_readsPaidAndLaterStates() {
        Order order = order(100L, 10L, 20L, 30_000L, 3_000L, PAID);
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(orderRepository.findByIdAndStateInWithWinnerAndSeller(eq(100L), anySet())).thenReturn(Optional.of(order));

        orderReadOrderUseCase.readOrder(1L, 100L);

        verify(orderRepository).findByIdAndStateInWithWinnerAndSeller(100L, readStates());
    }

    @Test
    @DisplayName("낙찰자도 판매자도 아닌 회원이 주문을 조회하면 ORDER_FORBIDDEN")
    void readOrder_byOther() {
        Order order = order(100L, 10L, 20L, 30_000L, 3_000L, PAID);
        when(orderSupport.getMember(3L)).thenReturn(other);
        when(orderRepository.findByIdAndStateInWithWinnerAndSeller(eq(100L), anySet())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderReadOrderUseCase.readOrder(3L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_FORBIDDEN);
    }

    @Test
    @DisplayName("조회할 수 있는 상태의 주문이 없으면 ORDER_NOT_FOUND")
    void readOrder_orderNotFound() {
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(orderRepository.findByIdAndStateInWithWinnerAndSeller(eq(100L), anySet())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderReadOrderUseCase.readOrder(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("주문 회원이 없으면 USER_NOT_FOUND이고, 주문은 조회하지 않는다")
    void readOrder_memberNotFound() {
        when(orderSupport.getMember(1L)).thenThrow(new BusinessException(USER_NOT_FOUND));

        assertThatThrownBy(() -> orderReadOrderUseCase.readOrder(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        verifyNoInteractions(orderRepository);
    }

    private Order order(Long id, Long auctionId, Long productId, Long winningPrice, Long deliveryFee, OrderState state) {
        Order order = new Order(new OrderGroup(winningPrice + deliveryFee), winner, seller, auctionId, productId, winningPrice, deliveryFee);
        order.updateState(state);
        ReflectionTestUtils.setField(order, "id", id);
        ReflectionTestUtils.setField(order, "createdAt", createdAt);
        ReflectionTestUtils.setField(order, "updatedAt", updatedAt);
        return order;
    }

    private Set<OrderState> readStates() {
        return Set.of(PAID, SHIPPING, SHIPPED, CONFIRMED, REFUND_REQUESTED, REFUNDED);
    }
}
