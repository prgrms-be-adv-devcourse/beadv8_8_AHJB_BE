package com.jjinmak.back.boundedContext.order.out;

import com.jjinmak.back.shared.cart.event.CartPaymentDefaultEvent;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.shared.order.event.OrderGroupDeletedEvent;
import com.jjinmak.back.shared.order.event.OrderPaymentSucceedEvent;
import com.jjinmak.back.shared.order.event.OrderRefundAcceptedEvent;
import com.jjinmak.back.shared.order.event.OrderRefundRequestedEvent;
import com.jjinmak.back.shared.order.event.OrderSendProductEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;

import static com.jjinmak.back.shared.delivery.domain.CarrierType.CJ;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderKafkaEventPublisherTest {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    OrderKafkaEventPublisher orderKafkaEventPublisher;

    LocalDateTime at = LocalDateTime.of(2026, 10, 30, 12, 0);

    @Test
    @DisplayName("구매확정 이벤트는 order.confirm-purchase 토픽에 주문 ID를 키로 보낸다")
    void confirmPurchase_sendsWithOrderIdKey() {
        OrderConfirmPurchaseEvent event = new OrderConfirmPurchaseEvent(1L, 2L, 3L, 4L, 5L, 30_000L, 3_000L, at);

        orderKafkaEventPublisher.publish(event);

        verify(kafkaTemplate).send("order.confirm-purchase", "1", event);
    }

    @Test
    @DisplayName("환불 요청 이벤트는 order.refund-requested 토픽에 주문 ID를 키로 보낸다")
    void refundRequested_sendsWithOrderIdKey() {
        OrderRefundRequestedEvent event = OrderRefundRequestedEvent.byWinner(1L, 2L, 3L, 5L, 30_000L, 3_000L);

        orderKafkaEventPublisher.publish(event);

        verify(kafkaTemplate).send("order.refund-requested", "1", event);
    }

    @Test
    @DisplayName("주문 그룹 삭제 이벤트는 order.group-deleted 토픽에 주문 그룹 ID를 키로 보낸다")
    void orderGroupDeleted_sendsWithOrderGroupIdKey() {
        OrderGroupDeletedEvent event = new OrderGroupDeletedEvent(7L, 33_000L);

        orderKafkaEventPublisher.publish(event);

        verify(kafkaTemplate).send("order.group-deleted", "7", event);
    }

    @Test
    @DisplayName("결제 성공 이벤트는 order.payment-succeed 토픽에 주문 ID를 키로 보낸다")
    void paymentSucceed_sendsWithOrderIdKey() {
        OrderPaymentSucceedEvent event = new OrderPaymentSucceedEvent(1L, 4L, 5L, at);

        orderKafkaEventPublisher.publish(event);

        verify(kafkaTemplate).send("order.payment-succeed", "1", event);
    }

    @Test
    @DisplayName("환불 수락 이벤트는 order.refund-accepted 토픽에 주문 ID를 키로 보낸다")
    void refundAccepted_sendsWithOrderIdKey() {
        OrderRefundAcceptedEvent event = new OrderRefundAcceptedEvent(1L, 4L);

        orderKafkaEventPublisher.publish(event);

        verify(kafkaTemplate).send("order.refund-accepted", "1", event);
    }

    @Test
    @DisplayName("발송 요청 이벤트는 order.send-product 토픽에 주문 ID를 키로 보낸다")
    void sendProduct_sendsWithOrderIdKey() {
        OrderSendProductEvent event = new OrderSendProductEvent(1L, 2L, 3L, CJ, "1234567890");

        orderKafkaEventPublisher.publish(event);

        verify(kafkaTemplate).send("order.send-product", "1", event);
    }

    @Test
    @DisplayName("장바구니 미결제 이벤트는 order.cart-payment-default 토픽에 경매 ID를 키로 보낸다")
    void cartPaymentDefault_sendsWithAuctionIdKey() {
        CartPaymentDefaultEvent event = new CartPaymentDefaultEvent(4L, 5L, 2L, 3L, 30_000L, 3_000L);

        orderKafkaEventPublisher.publish(event);

        verify(kafkaTemplate).send("order.cart-payment-default", "4", event);
    }
}
