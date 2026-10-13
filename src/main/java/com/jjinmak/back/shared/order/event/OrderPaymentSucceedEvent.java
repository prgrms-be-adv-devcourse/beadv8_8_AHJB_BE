package com.jjinmak.back.shared.order.event;

import java.time.LocalDateTime;

public record OrderPaymentSucceedEvent(
        Long orderId,
        Long productId,
        LocalDateTime paidAt
) {
}
