package com.jjinmak.notification.app.mapper;

import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.delivery.domain.DeliveryStatusCode;
import com.jjinmak.contracts.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.notification.domain.Notification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;

import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.*;
import static com.jjinmak.notification.domain.NotificationTargetType.ORDER;
import static com.jjinmak.notification.domain.NotificationType.DELIVERY_STATUS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryNotificationMapperTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 11, 16, 0);

    private final DeliveryNotificationMapper mapper = new DeliveryNotificationMapper();

    private DeliveryStatusUpdatedEvent 배송(Long winnerId, DeliveryStatusCode after) {
        return new DeliveryStatusUpdatedEvent(101L, winnerId, 9L, IN_TRANSIT, after, AT);
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatusCode.class, names = {"OUT_FOR_DELIVERY", "DELIVERED", "AVAILABLE_FOR_PICKUP", "ATTEMPT_FAIL", "EXCEPTION"})
    void 출발_완료_실패_문제_상태는_구매자에게_알린다(DeliveryStatusCode status) {
        Notification notification = mapper.fromStatusUpdated(배송(7L, status)).getFirst();

        assertThat(notification.getReceiverId()).isEqualTo(7L);
        assertThat(notification.getType()).isEqualTo(DELIVERY_STATUS);
        assertThat(notification.getTargetType()).isEqualTo(ORDER);
        assertThat(notification.getBody()).contains(status.getMessage());
        assertThat(notification.getDedupeKey()).isEqualTo("DELIVERY_STATUS:order-101:" + status.name() + ":member-7");
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatusCode.class, names = {"UNKNOWN", "WAITING", "INFORMATION_RECEIVED", "AT_PICKUP", "IN_TRANSIT", "WRONG"})
    void 중간_상태는_알리지_않는다(DeliveryStatusCode status) {
        assertThat(mapper.fromStatusUpdated(배송(7L, status))).isEmpty();
    }

    @Test
    void 같은_주문이라도_상태가_다르면_중복_방지_키가_다르다() {
        String outForDelivery = mapper.fromStatusUpdated(배송(7L, OUT_FOR_DELIVERY)).getFirst().getDedupeKey();
        String delivered = mapper.fromStatusUpdated(배송(7L, DELIVERED)).getFirst().getDedupeKey();

        assertThat(outForDelivery).isNotEqualTo(delivered);
    }

    @Test
    void 알릴_상태인데_구매자가_없으면_예외() {
        assertThatThrownBy(() -> mapper.fromStatusUpdated(배송(null, DELIVERED)))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void 중간_상태면_구매자가_없어도_예외_없이_건너뛴다() {
        assertThat(mapper.fromStatusUpdated(배송(null, IN_TRANSIT))).isEmpty();
    }
}
