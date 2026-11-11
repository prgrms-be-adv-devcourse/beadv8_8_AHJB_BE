package com.jjinmak.notification.app.mapper;

import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.refund.dto.RefundDto;
import com.jjinmak.contracts.refund.event.RefundCompletedEvent;
import com.jjinmak.contracts.refund.event.RefundRejectedEvent;
import com.jjinmak.notification.domain.Notification;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.jjinmak.notification.domain.NotificationTargetType.ORDER;
import static com.jjinmak.notification.domain.NotificationType.REFUND_COMPLETED_BUYER;
import static com.jjinmak.notification.domain.NotificationType.REFUND_REJECTED;

@Component
public class RefundNotificationMapper {

    public List<Notification> fromCompleted(RefundCompletedEvent event) {
        RefundDto refund = requireRefund(event.refund(), "환불 완료");
        return List.of(
                Notification.create(refund.winnerId(), REFUND_COMPLETED_BUYER,
                        "환불이 완료되었습니다", String.format("%,d원이 지갑으로 반환되었어요.", refund.amount()),
                        ORDER, refund.orderId(), "order-" + refund.orderId(), event.occurredAt())
        );
    }

    public List<Notification> fromRejected(RefundRejectedEvent event) {
        RefundDto refund = requireRefund(event.refund(), "환불 거절");
        return List.of(
                Notification.create(refund.winnerId(), REFUND_REJECTED,
                        "환불 요청이 거절되었습니다", "주문 상세에서 사유를 확인해 주세요.",
                        ORDER, refund.orderId(), "order-" + refund.orderId(), event.occurredAt())
        );
    }

    private RefundDto requireRefund(RefundDto refund, String eventName) {
        if (refund == null || refund.winnerId() == null) {
            throw new InvalidEventException(eventName + " 이벤트에 구매자 id가 없습니다.");
        }
        return refund;
    }
}