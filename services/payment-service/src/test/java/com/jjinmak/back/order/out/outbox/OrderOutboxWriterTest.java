package com.jjinmak.back.order.out.outbox;

import com.jjinmak.back.order.domain.OrderOutbox;
import com.jjinmak.back.order.out.repository.OrderOutboxRepository;
import com.jjinmak.contracts.cart.event.CartPaymentDefaultEvent;
import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.contracts.order.event.OrderGroupDeletedEvent;
import com.jjinmak.contracts.order.event.OrderPaymentSucceedEvent;
import com.jjinmak.contracts.order.event.OrderRefundAcceptedEvent;
import com.jjinmak.contracts.order.event.OrderRefundRequestedEvent;
import com.jjinmak.contracts.order.event.OrderRemindAutoConfirmEvent;
import com.jjinmak.contracts.order.event.OrderRemindPaymentEvent;
import com.jjinmak.contracts.order.event.OrderSendProductEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static com.jjinmak.contracts.delivery.domain.CarrierType.CJ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderOutboxWriterTest {

    @Mock
    OrderOutboxRepository orderOutboxRepository;

    ObjectMapper objectMapper = JsonMapper.builder().build();

    OrderOutboxWriter orderOutboxWriter;

    LocalDateTime at = LocalDateTime.of(2026, 10, 30, 12, 0);

    @BeforeEach
    void setUp() {
        orderOutboxWriter = new OrderOutboxWriter(orderOutboxRepository, objectMapper);
    }

    @Test
    @DisplayName("구매확정 이벤트는 order.confirm-purchase 토픽과 주문 ID 키로 아웃박스에 저장한다")
    void confirmPurchase_savesWithOrderIdKey() {
        OrderConfirmPurchaseEvent event = new OrderConfirmPurchaseEvent(1L, 2L, 3L, 4L, 5L, 30_000L, 3_000L, at);

        orderOutboxWriter.write(event);

        assertSaved("order.confirm-purchase", "1", event);
    }

    @Test
    @DisplayName("환불 요청 이벤트는 order.refund-requested 토픽과 주문 ID 키로 아웃박스에 저장한다")
    void refundRequested_savesWithOrderIdKey() {
        OrderRefundRequestedEvent event = OrderRefundRequestedEvent.byWinner(1L, 2L, 3L, 5L, 30_000L, 3_000L);

        orderOutboxWriter.write(event);

        assertSaved("order.refund-requested", "1", event);
    }

    @Test
    @DisplayName("주문 그룹 삭제 이벤트는 order.group-deleted 토픽과 주문 그룹 ID 키로 아웃박스에 저장한다")
    void orderGroupDeleted_savesWithOrderGroupIdKey() {
        OrderGroupDeletedEvent event = new OrderGroupDeletedEvent(7L, 33_000L);

        orderOutboxWriter.write(event);

        assertSaved("order.group-deleted", "7", event);
    }

    @Test
    @DisplayName("결제 성공 이벤트는 order.payment-succeed 토픽과 주문 ID 키로 아웃박스에 저장한다")
    void paymentSucceed_savesWithOrderIdKey() {
        OrderPaymentSucceedEvent event = new OrderPaymentSucceedEvent(1L, 4L, 5L, 2L, 3L, at);

        orderOutboxWriter.write(event);

        assertSaved("order.payment-succeed", "1", event);
    }

    @Test
    @DisplayName("환불 수락 이벤트는 order.refund-accepted 토픽과 주문 ID 키로 아웃박스에 저장한다")
    void refundAccepted_savesWithOrderIdKey() {
        OrderRefundAcceptedEvent event = new OrderRefundAcceptedEvent(1L, 4L);

        orderOutboxWriter.write(event);

        assertSaved("order.refund-accepted", "1", event);
    }

    @Test
    @DisplayName("발송 요청 이벤트는 order.send-product 토픽과 주문 ID 키로 아웃박스에 저장한다")
    void sendProduct_savesWithOrderIdKey() {
        OrderSendProductEvent event = new OrderSendProductEvent(1L, 2L, 3L, CJ, "1234567890");

        orderOutboxWriter.write(event);

        assertSaved("order.send-product", "1", event);
    }

    @Test
    @DisplayName("장바구니 미결제 이벤트는 order.cart-payment-default 토픽과 경매 ID 키로 아웃박스에 저장한다")
    void cartPaymentDefault_savesWithAuctionIdKey() {
        CartPaymentDefaultEvent event = new CartPaymentDefaultEvent(4L, 5L, 2L, 3L, 30_000L, 3_000L);

        orderOutboxWriter.write(event);

        assertSaved("order.cart-payment-default", "4", event);
    }

    @Test
    @DisplayName("결제 임박 알림 이벤트는 order.remind-payment-default 토픽과 장바구니 항목 ID 키로 아웃박스에 저장한다")
    void remindPayment_savesWithCartItemIdKey() {
        OrderRemindPaymentEvent event = new OrderRemindPaymentEvent(7L, 2L, 30_000L, 3_000L, at);

        orderOutboxWriter.write(event);

        assertSaved("order.remind-payment-default", "7", event);
    }

    @Test
    @DisplayName("자동 구매 확정 알림 이벤트는 order.remind-auto-confirm 토픽과 주문 ID 키로 아웃박스에 저장한다")
    void remindAutoConfirm_savesWithOrderIdKey() {
        OrderRemindAutoConfirmEvent event = new OrderRemindAutoConfirmEvent(1L, 2L, at);

        orderOutboxWriter.write(event);

        assertSaved("order.remind-auto-confirm", "1", event);
    }

    private void assertSaved(String topic, String key, Object event) {
        ArgumentCaptor<OrderOutbox> captor = ArgumentCaptor.forClass(OrderOutbox.class);
        verify(orderOutboxRepository).save(captor.capture());
        OrderOutbox saved = captor.getValue();

        assertThat(saved.getTopic()).isEqualTo(topic);
        assertThat(saved.getMessageKey()).isEqualTo(key);
        assertThat(objectMapper.readValue(saved.getPayload(), event.getClass())).isEqualTo(event);
        assertThat(saved.getPublishedAt()).isNull();
    }
}
