package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.OrderMemberRepository;
import com.jjinmak.back.boundedContext.order.out.OrderRepository;
import com.jjinmak.back.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_NOT_FOUND;
import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSupportTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    OrderMemberRepository orderMemberRepository;

    @InjectMocks
    OrderSupport orderSupport;

    @Test
    @DisplayName("회원 id로 주문 회원을 찾는다")
    void getMemberById_found() {
        OrderMember member = new OrderMember(1L, UUID.randomUUID(), "낙찰자");
        when(orderMemberRepository.findById(1L)).thenReturn(Optional.of(member));

        OrderMember found = orderSupport.getMember(1L);

        assertThat(found).isSameAs(member);
    }

    @Test
    @DisplayName("회원 id에 해당하는 주문 회원이 없으면 USER_NOT_FOUND")
    void getMemberById_notFound() {
        when(orderMemberRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderSupport.getMember(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);
    }

    @Test
    @DisplayName("회원 UUID로 주문 회원을 찾고, 회원 id로는 조회하지 않는다")
    void getMemberByUuid_found() {
        UUID uuid = UUID.randomUUID();
        OrderMember member = new OrderMember(1L, uuid, "낙찰자");
        when(orderMemberRepository.findByUuid(uuid)).thenReturn(Optional.of(member));

        OrderMember found = orderSupport.getMember(uuid);

        assertThat(found).isSameAs(member);
        verify(orderMemberRepository, never()).findById(any());
    }

    @Test
    @DisplayName("회원 UUID에 해당하는 주문 회원이 없으면 USER_NOT_FOUND, 회원 id로 다시 조회하지 않는다")
    void getMemberByUuid_notFound() {
        UUID uuid = UUID.randomUUID();
        when(orderMemberRepository.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderSupport.getMember(uuid))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        verify(orderMemberRepository, never()).findById(any());
    }

    @Test
    @DisplayName("주문 id로 주문을 찾는다")
    void getOrder_found() {
        Order order = order();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        Order found = orderSupport.getOrder(100L);

        assertThat(found).isSameAs(order);
    }

    @Test
    @DisplayName("주문 id에 해당하는 주문이 없으면 ORDER_NOT_FOUND")
    void getOrder_notFound() {
        when(orderRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderSupport.getOrder(100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("참여자와 함께 조회하면 낙찰자와 판매자를 같이 가져오는 쿼리로 주문을 찾고, 단순 id 조회는 하지 않는다")
    void getOrderWithParticipants_found() {
        Order order = order();
        when(orderRepository.findByIdWithWinnerAndSeller(100L)).thenReturn(Optional.of(order));

        Order found = orderSupport.getOrderWithParticipants(100L);

        assertThat(found).isSameAs(order);
        verify(orderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("참여자와 함께 조회할 주문이 없으면 ORDER_NOT_FOUND")
    void getOrderWithParticipants_notFound() {
        when(orderRepository.findByIdWithWinnerAndSeller(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderSupport.getOrderWithParticipants(100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_NOT_FOUND);
    }

    private Order order() {
        OrderMember winner = new OrderMember(1L, UUID.randomUUID(), "낙찰자");
        OrderMember seller = new OrderMember(2L, UUID.randomUUID(), "판매자");
        return new Order(new OrderGroup(33_000L), winner, seller, 10L, 20L, 30_000L, 3_000L);
    }
}
