package com.jjinmak.notification.app.mapper;

import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.cart.event.CartPaymentDefaultEvent;
import com.jjinmak.contracts.order.event.*;
import com.jjinmak.notification.domain.Notification;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.jjinmak.notification.domain.NotificationTargetType.*;
import static com.jjinmak.notification.domain.NotificationType.*;

@Component
public class OrderNotificationMapper {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    public List<Notification> fromPaymentDefault(CartPaymentDefaultEvent event) {
        require(event.winnerId(), event.sellerId(), "미결제", "auctionId=" + event.auctionId());
        String referenceKey = "auction-" + event.auctionId();
        return List.of(
                Notification.create(event.winnerId(), PAYMENT_DEFAULTED_BUYER,
                        "낙찰이 취소되었습니다", "결제 기한이 지나 낙찰이 취소되었어요.",
                        AUCTION, event.auctionId(), referenceKey, null),
                Notification.create(event.sellerId(), PAYMENT_DEFAULTED_SELLER,
                        "구매자 미결제로 거래가 취소되었습니다", "상품을 다시 등록할 수 있어요.",
                        PRODUCT, event.productId(), referenceKey, null)
        );
    }

    public List<Notification> fromRemindPayment(OrderRemindPaymentEvent event) {
        require(event.winnerId(), "결제 기한 임박", "cartItemId=" + event.cartItemId());
        return List.of(
                Notification.create(event.winnerId(), PAYMENT_DUE_SOON,
                        "결제 기한이 얼마 남지 않았어요",
                        event.paymentDueAt().format(TIME_FORMAT) + "까지 결제하지 않으면 낙찰이 취소돼요.",
                        CART, event.cartItemId(), "cart-item-" + event.cartItemId(), null)
        );
    }

    public List<Notification> fromPaymentSucceed(OrderPaymentSucceedEvent event) {
        require(event.sellerId(), "결제 완료", "orderId=" + event.orderId());
        return List.of(
                Notification.create(event.sellerId(), ORDER_PAID_SELLER,
                        "결제가 완료되었습니다", "상품을 발송하고 송장 번호를 등록해 주세요.",
                        ORDER, event.orderId(), "order-" + event.orderId(), event.paidAt())
        );
    }

    public List<Notification> fromSendProduct(OrderSendProductEvent event) {
        require(event.winnerId(), "발송", "orderId=" + event.orderId());
        String carrier = event.carrier() == null ? "택배" : event.carrier().getKorean();
        return List.of(
                Notification.create(event.winnerId(), ORDER_SHIPPED,
                        "상품이 발송되었습니다", carrier + " " + event.waybill() + "로 발송되었어요.",
                        ORDER, event.orderId(), "order-" + event.orderId(), null)
        );
    }

    public List<Notification> fromConfirmPurchase(OrderConfirmPurchaseEvent event) {
        require(event.sellerId(), "구매 확정", "orderId=" + event.orderId());
        return List.of(
                Notification.create(event.sellerId(), PURCHASE_CONFIRMED,
                        "구매가 확정되었습니다", "정산이 진행될 예정이에요.",
                        ORDER, event.orderId(), "order-" + event.orderId(), event.confirmedAt())
        );
    }

    public List<Notification> fromRemindAutoConfirm(OrderRemindAutoConfirmEvent event) {
        require(event.winnerId(), "자동 구매확정 임박", "orderId=" + event.orderId());
        return List.of(
                Notification.create(event.winnerId(), AUTO_CONFIRM_SOON,
                        "곧 자동으로 구매 확정돼요",
                        event.confirmExpiredAt().format(TIME_FORMAT) + "에 자동 구매 확정돼요. 문제가 있으면 그 전에 환불을 요청해 주세요.",
                        ORDER, event.orderId(), "order-" + event.orderId(), null)
        );
    }

    public List<Notification> fromRefundRequested(OrderRefundRequestedEvent event) {
        require(event.sellerId(), "환불 요청", "orderId=" + event.orderId());
        String referenceKey = "order-" + event.orderId();
        List<Notification> notifications = new ArrayList<>();
        notifications.add(Notification.create(event.sellerId(), REFUND_REQUESTED,
                "환불 요청이 들어왔습니다", "환불 요청 내용을 확인해 주세요.",
                ORDER, event.orderId(), referenceKey, null));
        if (event.refundType() == OrderRefundRequestedEvent.RefundType.SYSTEM_REQUEST && event.winnerId() != null) {
            notifications.add(Notification.create(event.winnerId(), REFUND_BY_SYSTEM,
                    "환불이 진행됩니다", "판매자가 기한 안에 발송하지 않아 환불을 진행해요.",
                    ORDER, event.orderId(), referenceKey, null));
        }
        return notifications;
    }

    private void require(Long receiverId, String eventName, String reference) {
        if (receiverId == null) {
            throw new InvalidEventException(eventName + " 이벤트에 받는 회원 id가 없습니다. " + reference);
        }
    }

    private void require(Long first, Long second, String eventName, String reference) {
        require(first, eventName, reference);
        require(second, eventName, reference);
    }
}