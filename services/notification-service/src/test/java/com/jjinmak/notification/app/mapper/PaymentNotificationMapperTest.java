package com.jjinmak.notification.app.mapper;

import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.payment.event.WalletReleasedEvent;
import com.jjinmak.notification.domain.Notification;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.jjinmak.notification.domain.NotificationCategory.SELL;
import static com.jjinmak.notification.domain.NotificationTargetType.SETTLEMENT;
import static com.jjinmak.notification.domain.NotificationType.SETTLEMENT_PAID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentNotificationMapperTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 11, 18, 0);

    private final PaymentNotificationMapper mapper = new PaymentNotificationMapper();

    @Test
    void 정산금_지급은_판매자에게_실지급액을_알린다() {
        Notification notification = mapper.fromReleased(
                new WalletReleasedEvent(101L, 9L, 53_000L, 2_650L, 50_350L, AT)).getFirst();

        assertThat(notification.getReceiverId()).isEqualTo(9L);
        assertThat(notification.getType()).isEqualTo(SETTLEMENT_PAID);
        assertThat(notification.getCategory()).isEqualTo(SELL);
        assertThat(notification.getTargetType()).isEqualTo(SETTLEMENT);
        assertThat(notification.getBody()).contains("50,350원");
    }

    @Test
    void 판매자가_없으면_예외() {
        assertThatThrownBy(() -> mapper.fromReleased(new WalletReleasedEvent(101L, null, 53_000L, 2_650L, 50_350L, AT)))
                .isInstanceOf(InvalidEventException.class);
    }
}
