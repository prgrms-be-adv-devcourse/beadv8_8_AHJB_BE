package com.jjinmak.contracts.order.event;

import java.time.LocalDateTime;

public record OrderRemindAutoConfirmEvent(
        Long orderId,
        Long winnerId,
        LocalDateTime confirmExpiredAt
) {
}
