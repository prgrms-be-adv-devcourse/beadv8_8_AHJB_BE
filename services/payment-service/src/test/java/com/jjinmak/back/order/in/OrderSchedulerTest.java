package com.jjinmak.back.order.in;

import com.jjinmak.back.order.app.dto.OutboxSendResult;
import com.jjinmak.back.order.domain.CartItem;
import com.jjinmak.back.order.domain.Order;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.order.app.OrderFacade;
import com.jjinmak.back.order.domain.OrderOutbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static com.jjinmak.back.order.exception.OrderErrorCode.CART_ITEM_NOT_FOUND;
import static com.jjinmak.back.order.exception.OrderErrorCode.ORDER_NOT_FOUND;
import static com.jjinmak.back.order.exception.OrderErrorCode.ORDER_STATE_BAD_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OrderSchedulerTest {

    @Mock
    OrderFacade orderFacade;

    @InjectMocks
    OrderScheduler orderScheduler;

    @Captor
    ArgumentCaptor<List<OrderOutbox>> sentOutboxes;

    @Test
    @DisplayName("한 건이 업무 예외로 실패해도 나머지 만료 장바구니 항목을 계속 처리한다")
    void deleteDefaultPayment_continuesAfterBusinessException() {
        when(orderFacade.findExpiredItemIds()).thenReturn(List.of(1L, 2L, 3L));
        lenient().doThrow(new BusinessException(CART_ITEM_NOT_FOUND)).when(orderFacade).deleteExpiredItem(2L);

        orderScheduler.deleteDefaultPayment();

        verify(orderFacade).deleteExpiredItem(1L);
        verify(orderFacade).deleteExpiredItem(2L);
        verify(orderFacade).deleteExpiredItem(3L);
    }

    @Test
    @DisplayName("한 건이 예상하지 못한 예외로 실패해도 나머지 만료 장바구니 항목을 계속 처리한다")
    void deleteDefaultPayment_continuesAfterUnexpectedException() {
        when(orderFacade.findExpiredItemIds()).thenReturn(List.of(1L, 2L, 3L));
        lenient().doThrow(new IllegalStateException("예상하지 못한 오류")).when(orderFacade).deleteExpiredItem(2L);

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
    @DisplayName("자동 구매 확정에서 다른 요청이 먼저 상태를 바꿨거나 낙관적 락에서 졌으면 INFO로, 다른 업무 예외는 ERROR로 남긴다")
    void confirmPurchaseAuto_logsRaceLossAtInfo(CapturedOutput output) {
        when(orderFacade.getExpiredOrderIds()).thenReturn(List.of(1L, 2L, 3L));
        lenient().doThrow(new BusinessException(ORDER_STATE_BAD_REQUEST)).when(orderFacade).confirmPurchaseAuto(1L);
        lenient().doThrow(new ObjectOptimisticLockingFailureException(Order.class, 2L)).when(orderFacade).confirmPurchaseAuto(2L);
        lenient().doThrow(new BusinessException(ORDER_NOT_FOUND)).when(orderFacade).confirmPurchaseAuto(3L);

        orderScheduler.confirmPurchaseAuto();

        assertThat(logEntries(output, "다른 요청이 먼저 처리해 자동 확정을 건너뜁니다."))
                .hasSize(2)
                .allMatch(entry -> entry.contains("INFO"))
                .anyMatch(entry -> entry.contains("orderId: 1") && entry.contains("errorCode: ORDER_STATE_BAD_REQUEST"))
                .anyMatch(entry -> entry.contains("orderId: 2"));
        assertThat(logEntries(output, "오류가 발생해 자동 확정을 진행하지 못했습니다."))
                .singleElement()
                .satisfies(entry -> assertThat(entry).contains("ERROR").contains("orderId: 3").contains("errorCode: ORDER_NOT_FOUND"));
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
    @DisplayName("미발송 자동 환불에서 다른 요청이 먼저 상태를 바꿨거나 낙관적 락에서 졌으면 INFO로, 다른 업무 예외는 ERROR로 남긴다")
    void refundNonShippedOrder_logsRaceLossAtInfo(CapturedOutput output) {
        when(orderFacade.getNonShippedOrderIds()).thenReturn(List.of(1L, 2L, 3L));
        lenient().doThrow(new BusinessException(ORDER_STATE_BAD_REQUEST)).when(orderFacade).requestSystemRefund(1L);
        lenient().doThrow(new ObjectOptimisticLockingFailureException(Order.class, 2L)).when(orderFacade).requestSystemRefund(2L);
        lenient().doThrow(new BusinessException(ORDER_NOT_FOUND)).when(orderFacade).requestSystemRefund(3L);

        orderScheduler.refundNonShippedOrder();

        assertThat(logEntries(output, "다른 요청이 먼저 처리해 자동 환불을 건너뜁니다."))
                .hasSize(2)
                .allMatch(entry -> entry.contains("INFO"))
                .anyMatch(entry -> entry.contains("orderId: 1") && entry.contains("errorCode: ORDER_STATE_BAD_REQUEST"))
                .anyMatch(entry -> entry.contains("orderId: 2"));
        assertThat(logEntries(output, "오류가 발생해 자동 환불을 진행하지 못했습니다."))
                .singleElement()
                .satisfies(entry -> assertThat(entry).contains("ERROR").contains("orderId: 3").contains("errorCode: ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("한 건이 업무 예외로 실패해도 나머지 결제 임박 항목에 계속 알림을 보낸다")
    void remindPayment_continuesAfterBusinessException() {
        when(orderFacade.findPaymentDueSoonItemIds()).thenReturn(List.of(1L, 2L, 3L));
        lenient().doThrow(new BusinessException(CART_ITEM_NOT_FOUND)).when(orderFacade).sendPaymentDueSoonReminder(2L);

        orderScheduler.remindPayment();

        verify(orderFacade).sendPaymentDueSoonReminder(1L);
        verify(orderFacade).sendPaymentDueSoonReminder(2L);
        verify(orderFacade).sendPaymentDueSoonReminder(3L);
    }

    @Test
    @DisplayName("한 건이 예상하지 못한 예외로 실패해도 나머지 결제 임박 항목에 계속 알림을 보낸다")
    void remindPayment_continuesAfterUnexpectedException() {
        when(orderFacade.findPaymentDueSoonItemIds()).thenReturn(List.of(1L, 2L, 3L));
        lenient().doThrow(new IllegalStateException("예상하지 못한 오류")).when(orderFacade).sendPaymentDueSoonReminder(2L);

        orderScheduler.remindPayment();

        verify(orderFacade).sendPaymentDueSoonReminder(1L);
        verify(orderFacade).sendPaymentDueSoonReminder(2L);
        verify(orderFacade).sendPaymentDueSoonReminder(3L);
    }

    @Test
    @DisplayName("결제 임박 항목이 없으면 아무 알림도 보내지 않는다")
    void remindPayment_noTargets() {
        when(orderFacade.findPaymentDueSoonItemIds()).thenReturn(List.of());

        orderScheduler.remindPayment();

        verify(orderFacade, never()).sendPaymentDueSoonReminder(anyLong());
    }

    @Test
    @DisplayName("결제 임박 알림에서 장바구니가 이미 결제돼 없거나 낙관적 락에서 졌으면 INFO로, 다른 업무 예외는 ERROR로 남긴다")
    void remindPayment_logsRaceLossAtInfo(CapturedOutput output) {
        when(orderFacade.findPaymentDueSoonItemIds()).thenReturn(List.of(1L, 2L, 3L));
        lenient().doThrow(new BusinessException(CART_ITEM_NOT_FOUND)).when(orderFacade).sendPaymentDueSoonReminder(1L);
        lenient().doThrow(new ObjectOptimisticLockingFailureException(CartItem.class, 2L)).when(orderFacade).sendPaymentDueSoonReminder(2L);
        lenient().doThrow(new BusinessException(ORDER_NOT_FOUND)).when(orderFacade).sendPaymentDueSoonReminder(3L);

        orderScheduler.remindPayment();

        assertThat(logEntries(output, "다른 요청이 먼저 처리해 결제 임박 알림을 건너뜁니다."))
                .hasSize(2)
                .allMatch(entry -> entry.contains("INFO"))
                .anyMatch(entry -> entry.contains("cartId: 1") && entry.contains("errorCode: CART_ITEM_NOT_FOUND"))
                .anyMatch(entry -> entry.contains("cartId: 2"));
        assertThat(logEntries(output, "오류가 발생해 결제 임박 알림을 전송하지 못했습니다."))
                .singleElement()
                .satisfies(entry -> assertThat(entry).contains("ERROR").contains("cartId: 3").contains("errorCode: ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("한 건이 업무 예외로 실패해도 나머지 구매 확정 임박 주문에 계속 알림을 보낸다")
    void remindAutoConfirm_continuesAfterBusinessException() {
        when(orderFacade.findExpiredSoonOrderIds()).thenReturn(List.of(1L, 2L, 3L));
        lenient().doThrow(new BusinessException(ORDER_STATE_BAD_REQUEST)).when(orderFacade).sendAutoConfirmReminder(2L);

        orderScheduler.remindAutoConfirm();

        verify(orderFacade).sendAutoConfirmReminder(1L);
        verify(orderFacade).sendAutoConfirmReminder(2L);
        verify(orderFacade).sendAutoConfirmReminder(3L);
    }

    @Test
    @DisplayName("한 건이 예상하지 못한 예외로 실패해도 나머지 구매 확정 임박 주문에 계속 알림을 보낸다")
    void remindAutoConfirm_continuesAfterUnexpectedException() {
        when(orderFacade.findExpiredSoonOrderIds()).thenReturn(List.of(1L, 2L, 3L));
        lenient().doThrow(new IllegalStateException("예상하지 못한 오류")).when(orderFacade).sendAutoConfirmReminder(2L);

        orderScheduler.remindAutoConfirm();

        verify(orderFacade).sendAutoConfirmReminder(1L);
        verify(orderFacade).sendAutoConfirmReminder(2L);
        verify(orderFacade).sendAutoConfirmReminder(3L);
    }

    @Test
    @DisplayName("구매 확정 임박 주문이 없으면 아무 알림도 보내지 않는다")
    void remindAutoConfirm_noTargets() {
        when(orderFacade.findExpiredSoonOrderIds()).thenReturn(List.of());

        orderScheduler.remindAutoConfirm();

        verify(orderFacade, never()).sendAutoConfirmReminder(anyLong());
    }

    @Test
    @DisplayName("구매 확정 임박 알림에서 다른 요청이 먼저 상태를 바꿨거나 낙관적 락에서 졌으면 INFO로, 다른 업무 예외는 ERROR로 남긴다")
    void remindAutoConfirm_logsRaceLossAtInfo(CapturedOutput output) {
        when(orderFacade.findExpiredSoonOrderIds()).thenReturn(List.of(1L, 2L, 3L));
        lenient().doThrow(new BusinessException(ORDER_STATE_BAD_REQUEST)).when(orderFacade).sendAutoConfirmReminder(1L);
        lenient().doThrow(new ObjectOptimisticLockingFailureException(Order.class, 2L)).when(orderFacade).sendAutoConfirmReminder(2L);
        lenient().doThrow(new BusinessException(ORDER_NOT_FOUND)).when(orderFacade).sendAutoConfirmReminder(3L);

        orderScheduler.remindAutoConfirm();

        assertThat(logEntries(output, "다른 요청이 먼저 처리해 구매 확정 알림을 건너뜁니다."))
                .hasSize(2)
                .allMatch(entry -> entry.contains("INFO"))
                .anyMatch(entry -> entry.contains("orderId: 1") && entry.contains("errorCode: ORDER_STATE_BAD_REQUEST"))
                .anyMatch(entry -> entry.contains("orderId: 2"));
        assertThat(logEntries(output, "오류가 발생해 구매 확정 알림을 전송하지 못했습니다."))
                .singleElement()
                .satisfies(entry -> assertThat(entry).contains("ERROR").contains("orderId: 3").contains("errorCode: ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("결제 대기 시간이 지난 주문 그룹을 하나씩 삭제한다")
    void deleteOverWaitingOrder_deletesEachGroup() {
        when(orderFacade.findOverWaitingGroupIds()).thenReturn(List.of(1L, 2L, 3L));

        orderScheduler.deleteOverWaitingOrder();

        verify(orderFacade).deleteOverWaitingGroup(1L);
        verify(orderFacade).deleteOverWaitingGroup(2L);
        verify(orderFacade).deleteOverWaitingGroup(3L);
    }

    @Test
    @DisplayName("한 주문 그룹이 업무 예외로 실패해도 나머지 결제 대기 시간이 지난 주문 그룹을 계속 삭제한다")
    void deleteOverWaitingOrder_continuesAfterBusinessException() {
        when(orderFacade.findOverWaitingGroupIds()).thenReturn(List.of(1L, 2L, 3L));
        lenient().doThrow(new BusinessException(ORDER_STATE_BAD_REQUEST)).when(orderFacade).deleteOverWaitingGroup(2L);

        orderScheduler.deleteOverWaitingOrder();

        verify(orderFacade).deleteOverWaitingGroup(1L);
        verify(orderFacade).deleteOverWaitingGroup(2L);
        verify(orderFacade).deleteOverWaitingGroup(3L);
    }

    @Test
    @DisplayName("한 주문 그룹이 예상하지 못한 예외로 실패해도 나머지 결제 대기 시간이 지난 주문 그룹을 계속 삭제한다")
    void deleteOverWaitingOrder_continuesAfterUnexpectedException() {
        when(orderFacade.findOverWaitingGroupIds()).thenReturn(List.of(1L, 2L, 3L));
        lenient().doThrow(new IllegalStateException("예상하지 못한 오류")).when(orderFacade).deleteOverWaitingGroup(2L);

        orderScheduler.deleteOverWaitingOrder();

        verify(orderFacade).deleteOverWaitingGroup(1L);
        verify(orderFacade).deleteOverWaitingGroup(2L);
        verify(orderFacade).deleteOverWaitingGroup(3L);
    }

    @Test
    @DisplayName("결제 대기 시간이 지난 주문 그룹이 없으면 아무것도 삭제하지 않는다")
    void deleteOverWaitingOrder_noTargets() {
        when(orderFacade.findOverWaitingGroupIds()).thenReturn(List.of());

        orderScheduler.deleteOverWaitingOrder();

        verify(orderFacade, never()).deleteOverWaitingGroup(anyLong());
    }

    @Test
    @DisplayName("발행하지 않은 아웃박스를 묶음으로 보내고, 보낸 id만 발행으로 표시한다")
    void sendOutbox_marksSentIds() {
        List<OrderOutbox> outboxes = outboxes(1, 3);
        when(orderFacade.findUnpublishedOutboxes()).thenReturn(outboxes);
        when(orderFacade.sendOutboxes(outboxes)).thenReturn(sentAll(outboxes));

        orderScheduler.sendOutbox();

        verify(orderFacade).markOutboxesPublished(List.of(1L, 2L, 3L));
        verify(orderFacade, times(1)).findUnpublishedOutboxes();
    }

    @Test
    @DisplayName("묶음이 100건으로 꽉 차면 같은 실행에서 다음 묶음을 보내고, 덜 찬 묶음을 보내면 그 실행을 끝낸다")
    void sendOutbox_continuesWhileBatchIsFull() {
        List<OrderOutbox> full = outboxes(1, 100);
        List<OrderOutbox> rest = outboxes(101, 105);
        when(orderFacade.findUnpublishedOutboxes()).thenReturn(full, rest);
        when(orderFacade.sendOutboxes(full)).thenReturn(sentAll(full));
        when(orderFacade.sendOutboxes(rest)).thenReturn(sentAll(rest));

        orderScheduler.sendOutbox();

        verify(orderFacade, times(2)).findUnpublishedOutboxes();
        verify(orderFacade).markOutboxesPublished(ids(full));
        verify(orderFacade).markOutboxesPublished(ids(rest));
    }

    @Test
    @DisplayName("묶음이 계속 꽉 차도 한 실행에서는 10묶음까지만 보낸다")
    void sendOutbox_sendsAtMostTenBatchesPerRun() {
        List<OrderOutbox> full = outboxes(1, 100);
        when(orderFacade.findUnpublishedOutboxes()).thenReturn(full);
        when(orderFacade.sendOutboxes(full)).thenReturn(sentAll(full));

        orderScheduler.sendOutbox();

        verify(orderFacade, times(10)).sendOutboxes(full);
    }

    @Test
    @DisplayName("전송 실패가 있으면 보낸 id만 발행으로 표시하고, 묶음이 꽉 차 있어도 그 실행을 끝낸다")
    void sendOutbox_stopsRunOnFailure() {
        List<OrderOutbox> full = outboxes(1, 100);
        when(orderFacade.findUnpublishedOutboxes()).thenReturn(full);
        when(orderFacade.sendOutboxes(full)).thenReturn(new OutboxSendResult(
                ids(full.subList(0, 50)), Map.of(full.get(50), new IllegalStateException("broker down"))));

        orderScheduler.sendOutbox();

        verify(orderFacade).markOutboxesPublished(ids(full.subList(0, 50)));
        verify(orderFacade, times(1)).findUnpublishedOutboxes();
    }

    @Test
    @DisplayName("실패가 있었던 다음 실행은 맨 앞 1건만 보내고, 그 1건이 성공하면 그다음 실행부터 다시 묶음으로 보낸다")
    void sendOutbox_retriesOnlyFirstAfterFailure() {
        OrderOutbox first = outbox(1L);
        OrderOutbox second = outbox(2L);
        OrderOutbox third = outbox(3L);
        when(orderFacade.findUnpublishedOutboxes()).thenReturn(
                List.of(first, second, third), List.of(first, second, third), List.of(second, third));
        when(orderFacade.sendOutboxes(anyList())).thenReturn(
                new OutboxSendResult(List.of(2L, 3L), Map.of(first, new IllegalStateException("broker down"))),
                new OutboxSendResult(List.of(1L), Map.of()),
                new OutboxSendResult(List.of(2L, 3L), Map.of()));

        orderScheduler.sendOutbox();
        orderScheduler.sendOutbox();
        orderScheduler.sendOutbox();

        verify(orderFacade, times(3)).sendOutboxes(sentOutboxes.capture());
        assertThat(sentOutboxes.getAllValues())
                .containsExactly(List.of(first, second, third), List.of(first), List.of(second, third));
    }

    @Test
    @DisplayName("맨 앞 1건이 다시 실패하면 다음 실행도 1건만 보낸다")
    void sendOutbox_keepsRetryingOnlyFirstWhileFailing() {
        OrderOutbox first = outbox(1L);
        OrderOutbox second = outbox(2L);
        when(orderFacade.findUnpublishedOutboxes()).thenReturn(List.of(first, second));
        when(orderFacade.sendOutboxes(anyList()))
                .thenReturn(new OutboxSendResult(List.of(), Map.of(first, new IllegalStateException("broker down"))));

        orderScheduler.sendOutbox();
        orderScheduler.sendOutbox();
        orderScheduler.sendOutbox();

        verify(orderFacade, times(3)).sendOutboxes(sentOutboxes.capture());
        assertThat(sentOutboxes.getAllValues())
                .containsExactly(List.of(first, second), List.of(first), List.of(first));
    }

    @Test
    @DisplayName("발행하지 않은 아웃박스가 없으면 아무것도 보내지 않는다")
    void sendOutbox_noTargets() {
        when(orderFacade.findUnpublishedOutboxes()).thenReturn(List.of());

        orderScheduler.sendOutbox();

        verify(orderFacade, never()).sendOutboxes(anyList());
        verify(orderFacade, never()).markOutboxesPublished(anyList());
    }

    private OrderOutbox outbox(Long id) {
        OrderOutbox outbox = new OrderOutbox("order.send-product", String.valueOf(id), "{}");
        ReflectionTestUtils.setField(outbox, "id", id);
        return outbox;
    }

    private List<OrderOutbox> outboxes(long fromId, long toId) {
        return LongStream.rangeClosed(fromId, toId).mapToObj(this::outbox).toList();
    }

    private List<Long> ids(List<OrderOutbox> outboxes) {
        return outboxes.stream().map(OrderOutbox::getId).toList();
    }

    private OutboxSendResult sentAll(List<OrderOutbox> outboxes) {
        return new OutboxSendResult(ids(outboxes), Map.of());
    }

    private List<String> logEntries(CapturedOutput output, String message) {
        List<String> lines = output.getAll().lines().toList();
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).contains(message))
                continue;
            StringBuilder entry = new StringBuilder(lines.get(i));
            for (int j = i + 1; j < lines.size() && lines.get(j).matches("(cartId|orderId|errorCode): .*"); j++)
                entry.append('\n').append(lines.get(j));
            entries.add(entry.toString());
        }
        return entries;
    }
}
