package com.jjinmak.back.order.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.dto.PaymentCloseResultDto;
import com.jjinmak.back.order.domain.OrderGroup;
import com.jjinmak.back.order.domain.OrderMember;
import com.jjinmak.back.order.out.payment.PaymentRequest;
import com.jjinmak.back.order.out.repository.OrderGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static com.jjinmak.back.order.exception.OrderErrorCode.ORDER_GROUP_FORBIDDEN;
import static com.jjinmak.back.order.exception.OrderErrorCode.ORDER_STATE_BAD_REQUEST;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartCancelPaymentUseCaseTest {

    @Mock
    PaymentRequest paymentRequest;

    @Mock
    OrderGroupRepository orderGroupRepository;

    @Mock
    OrderSupport orderSupport;

    @InjectMocks
    CartCancelPaymentUseCase cartCancelPaymentUseCase;

    OrderGroup group;

    @BeforeEach
    void setUp() {
        group = new OrderGroup(new OrderMember(1L, UUID.randomUUID(), "낙찰자"), 33_000L);
        ReflectionTestUtils.setField(group, "id", 50L);
    }

    @Test
    @DisplayName("결제를 닫았으면(CLOSED) 주문 그룹 id와 낙찰자 id로 결제를 닫은 뒤 주문 그룹을 삭제한다")
    void cancelPayment_closed() {
        when(orderSupport.getOrderGroup(50L)).thenReturn(group);
        when(paymentRequest.closePayment(50L, 1L)).thenReturn(PaymentCloseResultDto.closed(50L, 7L));

        cartCancelPaymentUseCase.cancelPayment(1L, 50L);

        InOrder inOrder = inOrder(paymentRequest, orderGroupRepository);
        inOrder.verify(paymentRequest).closePayment(50L, 1L);
        inOrder.verify(orderGroupRepository).delete(group);
    }

    @Test
    @DisplayName("결제 요청 전이라 결제가 없으면(NOT_FOUND) 주문 그룹을 삭제한다")
    void cancelPayment_noPayment() {
        when(orderSupport.getOrderGroup(50L)).thenReturn(group);
        when(paymentRequest.closePayment(50L, 1L)).thenReturn(PaymentCloseResultDto.notFound(50L));

        cartCancelPaymentUseCase.cancelPayment(1L, 50L);

        verify(orderGroupRepository).delete(group);
    }

    @Test
    @DisplayName("이미 결제가 끝났으면(ALREADY_PAID) ORDER_STATE_BAD_REQUEST, 주문 그룹을 삭제하지 않는다")
    void cancelPayment_alreadyPaid() {
        when(orderSupport.getOrderGroup(50L)).thenReturn(group);
        when(paymentRequest.closePayment(50L, 1L)).thenReturn(PaymentCloseResultDto.alreadyPaid(50L, 7L));

        assertThatThrownBy(() -> cartCancelPaymentUseCase.cancelPayment(1L, 50L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);

        verify(orderGroupRepository, never()).delete(any());
    }

    @Test
    @DisplayName("다른 회원의 주문 그룹이면 ORDER_GROUP_FORBIDDEN, 결제를 닫지 않고 주문 그룹을 삭제하지 않는다")
    void cancelPayment_othersGroup() {
        when(orderSupport.getOrderGroup(50L)).thenReturn(group);

        assertThatThrownBy(() -> cartCancelPaymentUseCase.cancelPayment(3L, 50L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_GROUP_FORBIDDEN);

        verifyNoInteractions(paymentRequest);
        verify(orderGroupRepository, never()).delete(any());
    }

    @Test
    @DisplayName("결제를 닫다가 결제 쪽 예외(PAYMENT_IN_PROGRESS)가 나면 그대로 올리고 주문 그룹을 삭제하지 않는다")
    void cancelPayment_paymentInProgress() {
        BusinessException inProgress = new BusinessException(PAYMENT_IN_PROGRESS);
        when(orderSupport.getOrderGroup(50L)).thenReturn(group);
        when(paymentRequest.closePayment(50L, 1L)).thenThrow(inProgress);

        assertThatThrownBy(() -> cartCancelPaymentUseCase.cancelPayment(1L, 50L))
                .isSameAs(inProgress);

        verify(orderGroupRepository, never()).delete(any());
    }
}
