package com.jjinmak.notification.app;

import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.cart.event.CartPaymentDefaultEvent;
import com.jjinmak.contracts.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.contracts.order.event.*;
import com.jjinmak.contracts.payment.event.WalletReleasedEvent;
import com.jjinmak.contracts.refund.event.RefundCompletedEvent;
import com.jjinmak.contracts.refund.event.RefundRejectedEvent;
import com.jjinmak.notification.app.mapper.*;
import com.jjinmak.notification.domain.Notification;
import com.jjinmak.notification.domain.NotificationCategory;
import com.jjinmak.notification.dto.NotificationListResponseDto;
import com.jjinmak.notification.dto.NotificationSettingsResponseDto;
import com.jjinmak.notification.dto.NotificationSettingsUpdateRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationFacade {

    private final AuctionNotificationMapper auctionNotificationMapper;
    private final OrderNotificationMapper orderNotificationMapper;
    private final DeliveryNotificationMapper deliveryNotificationMapper;
    private final RefundNotificationMapper refundNotificationMapper;
    private final PaymentNotificationMapper paymentNotificationMapper;
    private final NotificationCreateUseCase notificationCreateUseCase;
    private final NotificationQueryUseCase notificationQueryUseCase;
    private final NotificationReadUseCase notificationReadUseCase;
    private final NotificationDeviceUseCase notificationDeviceUseCase;
    private final NotificationSettingUseCase notificationSettingUseCase;

    @Transactional
    public int notifyAuctionWon(AuctionWonEvent event, LocalDateTime now) {
        return save(auctionNotificationMapper.fromWon(event), now);
    }

    @Transactional
    public int notifyAuctionFailed(AuctionFailedEvent event, LocalDateTime now) {
        return save(auctionNotificationMapper.fromFailed(event), now);
    }

    @Transactional
    public int notifyPaymentDefault(CartPaymentDefaultEvent event, LocalDateTime now) {
        return save(orderNotificationMapper.fromPaymentDefault(event), now);
    }

    @Transactional
    public int notifyRemindPayment(OrderRemindPaymentEvent event, LocalDateTime now) {
        return save(orderNotificationMapper.fromRemindPayment(event), now);
    }

    @Transactional
    public int notifyPaymentSucceed(OrderPaymentSucceedEvent event, LocalDateTime now) {
        return save(orderNotificationMapper.fromPaymentSucceed(event), now);
    }

    @Transactional
    public int notifySendProduct(OrderSendProductEvent event, LocalDateTime now) {
        return save(orderNotificationMapper.fromSendProduct(event), now);
    }

    @Transactional
    public int notifyConfirmPurchase(OrderConfirmPurchaseEvent event, LocalDateTime now) {
        return save(orderNotificationMapper.fromConfirmPurchase(event), now);
    }

    @Transactional
    public int notifyRemindAutoConfirm(OrderRemindAutoConfirmEvent event, LocalDateTime now) {
        return save(orderNotificationMapper.fromRemindAutoConfirm(event), now);
    }

    @Transactional
    public int notifyRefundRequested(OrderRefundRequestedEvent event, LocalDateTime now) {
        return save(orderNotificationMapper.fromRefundRequested(event), now);
    }

    @Transactional
    public int notifyDeliveryStatus(DeliveryStatusUpdatedEvent event, LocalDateTime now) {
        return save(deliveryNotificationMapper.fromStatusUpdated(event), now);
    }

    @Transactional
    public int notifyRefundCompleted(RefundCompletedEvent event, LocalDateTime now) {
        return save(refundNotificationMapper.fromCompleted(event), now);
    }

    @Transactional
    public int notifyRefundRejected(RefundRejectedEvent event, LocalDateTime now) {
        return save(refundNotificationMapper.fromRejected(event), now);
    }

    @Transactional
    public int notifySettlementPaid(WalletReleasedEvent event, LocalDateTime now) {
        return save(paymentNotificationMapper.fromReleased(event), now);
    }

    @Transactional(readOnly = true)
    public NotificationListResponseDto list(Long memberId, NotificationCategory category, Long cursor, Integer size) {
        return notificationQueryUseCase.list(memberId, category, cursor, size);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long memberId) {
        return notificationQueryUseCase.unreadCount(memberId);
    }

    @Transactional
    public void read(Long memberId, Long notificationId, LocalDateTime now) {
        notificationReadUseCase.read(memberId, notificationId, now);
    }

    @Transactional
    public int readAll(Long memberId, LocalDateTime now) {
        return notificationReadUseCase.readAll(memberId, now);
    }

    private int save(List<Notification> notifications, LocalDateTime now) {
        return notificationCreateUseCase.create(notifications, now);
    }

    @Transactional
    public void registerDevice(Long memberId, String token, LocalDateTime now) {
        notificationDeviceUseCase.register(memberId, token, now);
    }

    @Transactional
    public void unregisterDevice(Long memberId, String token) {
        notificationDeviceUseCase.unregister(memberId, token);
    }

    @Transactional(readOnly = true)
    public NotificationSettingsResponseDto getSettings(Long memberId) {
        return notificationSettingUseCase.get(memberId);
    }

    @Transactional
    public NotificationSettingsResponseDto updateSettings(Long memberId,
                                                          List<NotificationSettingsUpdateRequestDto.Item> items,
                                                          LocalDateTime now) {
        return notificationSettingUseCase.update(memberId, items, now);
    }
}