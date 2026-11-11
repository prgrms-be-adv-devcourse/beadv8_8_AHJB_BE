package com.jjinmak.notification.app.mapper;

import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.payment.event.WalletReleasedEvent;
import com.jjinmak.notification.domain.Notification;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.jjinmak.notification.domain.NotificationTargetType.SETTLEMENT;
import static com.jjinmak.notification.domain.NotificationType.SETTLEMENT_PAID;

@Component
public class PaymentNotificationMapper {

    public List<Notification> fromReleased(WalletReleasedEvent event) {
        if (event.sellerId() == null) {
            throw new InvalidEventException("정산 이벤트에 판매자 id가 없습니다. orderId=" + event.orderId());
        }
        return List.of(
                Notification.create(event.sellerId(), SETTLEMENT_PAID,
                        "정산금이 지급되었습니다", String.format("%,d원이 지급되었어요.", event.settlementAmount()),
                        SETTLEMENT, event.orderId(), "order-" + event.orderId(), event.releasedAt())
        );
    }
}