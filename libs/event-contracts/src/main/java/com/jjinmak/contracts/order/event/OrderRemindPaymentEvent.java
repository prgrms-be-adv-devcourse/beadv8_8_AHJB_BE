package com.jjinmak.contracts.order.event;

import java.time.LocalDateTime;

public record OrderRemindPaymentEvent(
        Long cartItemId,
        Long winnerId,
        Long winningPrice,
        Long shippingFee,
        LocalDateTime paymentDueAt
) {
}
