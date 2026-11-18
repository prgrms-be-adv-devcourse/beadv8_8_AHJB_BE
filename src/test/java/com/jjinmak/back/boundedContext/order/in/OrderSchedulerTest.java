package com.jjinmak.back.boundedContext.order.in;

import com.jjinmak.back.boundedContext.order.app.OrderFacade;
import com.jjinmak.back.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.CART_ITEM_NOT_FOUND;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_STATE_BAD_REQUEST;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSchedulerTest {

    @Mock
    OrderFacade orderFacade;

    @InjectMocks
    OrderScheduler orderScheduler;

    @Test
    @DisplayName("한 건이 업무 예외로 실패해도 나머지 만료 장바구니 항목을 계속 처리한다")
    void deleteDefaultPayment_continuesAfterBusinessException() {
        when(orderFacade.findExpiredItemIds()).thenReturn(List.of(1L, 2L, 3L));
        doThrow(new BusinessException(CART_ITEM_NOT_FOUND)).when(orderFacade).deleteExpiredItem(2L);

        orderScheduler.deleteDefaultPayment();

        verify(orderFacade).deleteExpiredItem(1L);
        verify(orderFacade).deleteExpiredItem(2L);
        verify(orderFacade).deleteExpiredItem(3L);
    }

    @Test
    @DisplayName("한 건이 예상하지 못한 예외로 실패해도 나머지 만료 장바구니 항목을 계속 처리한다")
    void deleteDefaultPayment_continuesAfterUnexpectedException() {
        when(orderFacade.findExpiredItemIds()).thenReturn(List.of(1L, 2L, 3L));
        doThrow(new IllegalStateException("예상하지 못한 오류")).when(orderFacade).deleteExpiredItem(2L);

        orderScheduler.deleteDefaultPayment();

        verify(orderFacade).deleteExpiredItem(1L);
        verify(orderFacade).deleteExpiredItem(2L);
        verify(orderFacade).deleteExpiredItem(3L);
    }

    @Test
    @DisplayName("만료 장바구니 항목이 없으면 아무것도 삭제하지 않는다")
    void deleteDefaultPayment_noTargets() {
        when(orderFacade.findExpiredItemIds()).thenReturn(List.of());

        orderScheduler.deleteDefaultPayment();

        verify(orderFacade, never()).deleteExpiredItem(anyLong());
    }

    @Test
    @DisplayName("한 건이 업무 예외로 실패해도 나머지 만료 주문을 계속 자동 구매 확정한다")
    void confirmPurchaseAuto_continuesAfterBusinessException() {
        when(orderFacade.getExpiredOrderIds()).thenReturn(List.of(1L, 2L, 3L));
        lenient().doThrow(new BusinessException(ORDER_STATE_BAD_REQUEST)).when(orderFacade).confirmPurchaseAuto(2L);

        orderScheduler.confirmPurchaseAuto();

        verify(orderFacade).confirmPurchaseAuto(1L);
        verify(orderFacade).confirmPurchaseAuto(2L);
        verify(orderFacade).confirmPurchaseAuto(3L);
    }

    @Test
    @DisplayName("한 건이 예상하지 못한 예외로 실패해도 나머지 만료 주문을 계속 자동 구매 확정한다")
    void confirmPurchaseAuto_continuesAfterUnexpectedException() {
        when(orderFacade.getExpiredOrderIds()).thenReturn(List.of(1L, 2L, 3L));
        lenient().doThrow(new IllegalStateException("예상하지 못한 오류")).when(orderFacade).confirmPurchaseAuto(2L);

        orderScheduler.confirmPurchaseAuto();

        verify(orderFacade).confirmPurchaseAuto(1L);
        verify(orderFacade).confirmPurchaseAuto(2L);
        verify(orderFacade).confirmPurchaseAuto(3L);
    }

    @Test
    @DisplayName("만료 주문이 없으면 아무것도 자동 구매 확정하지 않는다")
    void confirmPurchaseAuto_noTargets() {
        when(orderFacade.getExpiredOrderIds()).thenReturn(List.of());

        orderScheduler.confirmPurchaseAuto();

        verify(orderFacade, never()).confirmPurchaseAuto(anyLong());
    }

    @Test
    @DisplayName("한 건이 업무 예외로 실패해도 나머지 미발송 주문에 계속 시스템 환불을 요청한다")
    void refundNonShippedOrder_continuesAfterBusinessException() {
        when(orderFacade.getNonShippedOrderIds()).thenReturn(List.of(1L, 2L, 3L));
        lenient().doThrow(new BusinessException(ORDER_STATE_BAD_REQUEST)).when(orderFacade).requestSystemRefund(2L);

        orderScheduler.refundNonShippedOrder();

        verify(orderFacade).requestSystemRefund(1L);
        verify(orderFacade).requestSystemRefund(2L);
        verify(orderFacade).requestSystemRefund(3L);
    }

    @Test
    @DisplayName("한 건이 예상하지 못한 예외로 실패해도 나머지 미발송 주문에 계속 시스템 환불을 요청한다")
    void refundNonShippedOrder_continuesAfterUnexpectedException() {
        when(orderFacade.getNonShippedOrderIds()).thenReturn(List.of(1L, 2L, 3L));
        lenient().doThrow(new IllegalStateException("예상하지 못한 오류")).when(orderFacade).requestSystemRefund(2L);

        orderScheduler.refundNonShippedOrder();

        verify(orderFacade).requestSystemRefund(1L);
        verify(orderFacade).requestSystemRefund(2L);
        verify(orderFacade).requestSystemRefund(3L);
    }

    @Test
    @DisplayName("미발송 주문이 없으면 아무것도 시스템 환불을 요청하지 않는다")
    void refundNonShippedOrder_noTargets() {
        when(orderFacade.getNonShippedOrderIds()).thenReturn(List.of());

        orderScheduler.refundNonShippedOrder();

        verify(orderFacade, never()).requestSystemRefund(anyLong());
    }

    @Test
    @DisplayName("결제 대기 시간이 지난 주문 삭제를 요청한다")
    void deleteOverWaitingOrder_deletesOverWaitingOrders() {
        orderScheduler.deleteOverWaitingOrder();

        verify(orderFacade).deleteOverWaitingOrders();
    }

    @Test
    @DisplayName("결제 대기 시간이 지난 주문 삭제가 실패해도 예외를 밖으로 던지지 않는다")
    void deleteOverWaitingOrder_swallowsException() {
        doThrow(new IllegalStateException("예상하지 못한 오류")).when(orderFacade).deleteOverWaitingOrders();

        orderScheduler.deleteOverWaitingOrder();

        verify(orderFacade).deleteOverWaitingOrders();
    }
}
