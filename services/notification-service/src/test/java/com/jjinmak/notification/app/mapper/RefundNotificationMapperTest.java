package com.jjinmak.notification.app.mapper;

import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.refund.domain.RefundStatus;
import com.jjinmak.contracts.refund.dto.RefundDto;
import com.jjinmak.contracts.refund.event.RefundCompletedEvent;
import com.jjinmak.contracts.refund.event.RefundRejectedEvent;
import com.jjinmak.notification.domain.Notification;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.jjinmak.notification.domain.NotificationTargetType.ORDER;
import static com.jjinmak.notification.domain.NotificationType.REFUND_COMPLETED_BUYER;
import static com.jjinmak.notification.domain.NotificationType.REFUND_REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundNotificationMapperTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 11, 19, 0);

    private final RefundNotificationMapper mapper = new RefundNotificationMapper();

    private RefundDto 환불(Long winnerId, RefundStatus status) {
        return new RefundDto(1L, 102L, winnerId, status, 53_000L, AT.minusMinutes(30), AT);
    }

    @Test
    void 환불_완료는_구매자에게_환불_금액과_함께_알린다() {
        Notification notification = mapper.fromCompleted(
                new RefundCompletedEvent("e-1", AT, 환불(7L, RefundStatus.COMPLETED))).getFirst();

        assertThat(notification.getReceiverId()).isEqualTo(7L);
        assertThat(notification.getType()).isEqualTo(REFUND_COMPLETED_BUYER);
        assertThat(notification.getTargetType()).isEqualTo(ORDER);
        assertThat(notification.getTargetId()).isEqualTo(102L);
        assertThat(notification.getBody()).contains("53,000원");
        assertThat(notification.getEventOccurredAt()).isEqualTo(AT);
    }

    @Test
    void 환불_거절은_구매자에게_알린다() {
        Notification notification = mapper.fromRejected(
                new RefundRejectedEvent("e-2", AT, 환불(7L, RefundStatus.REJECTED))).getFirst();

        assertThat(notification.getReceiverId()).isEqualTo(7L);
        assertThat(notification.getType()).isEqualTo(REFUND_REJECTED);
    }

    @Test
    void 환불_정보나_구매자가_없으면_예외() {
        assertThatThrownBy(() -> mapper.fromCompleted(new RefundCompletedEvent("e-3", AT, null)))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> mapper.fromRejected(new RefundRejectedEvent("e-4", AT, 환불(null, RefundStatus.REJECTED))))
                .isInstanceOf(InvalidEventException.class);
    }
}
