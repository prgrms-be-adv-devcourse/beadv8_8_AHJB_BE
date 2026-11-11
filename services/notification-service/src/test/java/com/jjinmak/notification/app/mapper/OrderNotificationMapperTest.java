package com.jjinmak.notification.app.mapper;

import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.cart.event.CartPaymentDefaultEvent;
import com.jjinmak.contracts.delivery.domain.CarrierType;
import com.jjinmak.contracts.order.event.*;
import com.jjinmak.notification.domain.Notification;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.jjinmak.notification.domain.NotificationCategory.BUY;
import static com.jjinmak.notification.domain.NotificationCategory.SELL;
import static com.jjinmak.notification.domain.NotificationTargetType.*;
import static com.jjinmak.notification.domain.NotificationType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

class OrderNotificationMapperTest {

    private static final Long BUYER = 7L;
    private static final Long SELLER = 9L;
    private static final Long ORDER_ID = 101L;
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 11, 15, 0);

    private final OrderNotificationMapper mapper = new OrderNotificationMapper();

    @Test
    void 미결제면_낙찰자는_경매_상세로_판매자는_상품_상세로_알린다() {
        List<Notification> notifications = mapper.fromPaymentDefault(
                new CartPaymentDefaultEvent(21L, 6L, BUYER, SELLER, 50_000L, 3_000L));

        assertThat(notifications)
                .extracting(Notification::getReceiverId, Notification::getType, Notification::getCategory,
                        Notification::getTargetType, Notification::getTargetId)
                .containsExactly(
                        tuple(BUYER, PAYMENT_DEFAULTED_BUYER, BUY, AUCTION, 21L),
                        tuple(SELLER, PAYMENT_DEFAULTED_SELLER, SELL, PRODUCT, 6L));
    }

    @Test
    void 미결제_이벤트에_받는_회원이_없으면_예외() {
        assertThatThrownBy(() -> mapper.fromPaymentDefault(
                new CartPaymentDefaultEvent(21L, 6L, BUYER, null, 50_000L, 3_000L)))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void 결제_기한_임박은_낙찰자에게_장바구니로_가는_알림과_기한을_보여준다() {
        Notification notification = mapper.fromRemindPayment(
                new OrderRemindPaymentEvent(31L, BUYER, 50_000L, 3_000L, LocalDateTime.of(2026, 11, 12, 12, 0))).getFirst();

        assertThat(notification.getReceiverId()).isEqualTo(BUYER);
        assertThat(notification.getType()).isEqualTo(PAYMENT_DUE_SOON);
        assertThat(notification.getTargetType()).isEqualTo(CART);
        assertThat(notification.getTargetId()).isEqualTo(31L);
        assertThat(notification.getBody()).startsWith("11월 12일 12:00까지");
        assertThat(notification.getDedupeKey()).isEqualTo("PAYMENT_DUE_SOON:cart-item-31:member-7");
    }

    @Test
    void 결제_완료는_판매자에게_발송_요청으로_알린다() {
        Notification notification = mapper.fromPaymentSucceed(
                new OrderPaymentSucceedEvent(ORDER_ID, 21L, 6L, BUYER, SELLER, AT)).getFirst();

        assertThat(notification.getReceiverId()).isEqualTo(SELLER);
        assertThat(notification.getType()).isEqualTo(ORDER_PAID_SELLER);
        assertThat(notification.getTargetType()).isEqualTo(ORDER);
        assertThat(notification.getEventOccurredAt()).isEqualTo(AT);
    }

    @Test
    void 결제_완료_이벤트에_판매자가_없으면_예외() {
        assertThatThrownBy(() -> mapper.fromPaymentSucceed(
                new OrderPaymentSucceedEvent(ORDER_ID, 21L, 6L, BUYER, null, AT)))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void 발송은_구매자에게_택배사와_송장번호를_알린다() {
        Notification notification = mapper.fromSendProduct(
                new OrderSendProductEvent(ORDER_ID, BUYER, SELLER, CarrierType.CJ, "123456789")).getFirst();

        assertThat(notification.getReceiverId()).isEqualTo(BUYER);
        assertThat(notification.getType()).isEqualTo(ORDER_SHIPPED);
        assertThat(notification.getBody()).contains("대한통운", "123456789");
    }

    @Test
    void 구매_확정은_판매자에게_알린다() {
        Notification notification = mapper.fromConfirmPurchase(
                new OrderConfirmPurchaseEvent(ORDER_ID, BUYER, SELLER, 21L, 6L, 50_000L, 3_000L, AT)).getFirst();

        assertThat(notification.getReceiverId()).isEqualTo(SELLER);
        assertThat(notification.getType()).isEqualTo(PURCHASE_CONFIRMED);
        assertThat(notification.getTargetId()).isEqualTo(ORDER_ID);
    }

    @Test
    void 자동_구매확정_임박은_구매자에게_확정_시각을_알린다() {
        Notification notification = mapper.fromRemindAutoConfirm(
                new OrderRemindAutoConfirmEvent(ORDER_ID, BUYER, LocalDateTime.of(2026, 11, 14, 15, 0))).getFirst();

        assertThat(notification.getReceiverId()).isEqualTo(BUYER);
        assertThat(notification.getType()).isEqualTo(AUTO_CONFIRM_SOON);
        assertThat(notification.getBody()).startsWith("11월 14일 15:00");
    }

    @Test
    void 구매자가_요청한_환불은_판매자에게만_알린다() {
        List<Notification> notifications = mapper.fromRefundRequested(
                OrderRefundRequestedEvent.byWinner(ORDER_ID, BUYER, SELLER, 6L, 50_000L, 3_000L));

        assertThat(notifications)
                .extracting(Notification::getReceiverId, Notification::getType)
                .containsExactly(tuple(SELLER, REFUND_REQUESTED));
    }

    @Test
    void 시스템_환불은_판매자와_구매자_모두에게_알린다() {
        List<Notification> notifications = mapper.fromRefundRequested(
                OrderRefundRequestedEvent.bySystem(ORDER_ID, BUYER, SELLER, 6L, 50_000L, 3_000L));

        assertThat(notifications)
                .extracting(Notification::getReceiverId, Notification::getType, Notification::getCategory)
                .containsExactly(
                        tuple(SELLER, REFUND_REQUESTED, SELL),
                        tuple(BUYER, REFUND_BY_SYSTEM, BUY));
    }
}
