package com.jjinmak.notification.app.mapper;

import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.delivery.domain.DeliveryStatusCode;
import com.jjinmak.contracts.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.notification.domain.Notification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.*;
import static com.jjinmak.notification.domain.NotificationTargetType.ORDER;
import static com.jjinmak.notification.domain.NotificationType.DELIVERY_STATUS;

@Component
public class DeliveryNotificationMapper {

    private static final Set<DeliveryStatusCode> NOTIFY_STATUSES =
            Set.of(OUT_FOR_DELIVERY, DELIVERED, AVAILABLE_FOR_PICKUP, ATTEMPT_FAIL, EXCEPTION);

    public List<Notification> fromStatusUpdated(DeliveryStatusUpdatedEvent event) {
        DeliveryStatusCode status = event.afterStatus();
        if (status == null || !NOTIFY_STATUSES.contains(status)) {
            return List.of();
        }
        if (event.winnerId() == null) {
            throw new InvalidEventException("배송 상태 이벤트에 구매자 id가 없습니다. orderId=" + event.orderId());
        }
        return List.of(
                Notification.create(event.winnerId(), DELIVERY_STATUS,
                        "배송 상태가 바뀌었습니다", "주문하신 상품이 '" + status.getMessage() + "' 상태예요.",
                        ORDER, event.orderId(), "order-" + event.orderId() + ":" + status.name(), event.updatedAt())
        );
    }
}