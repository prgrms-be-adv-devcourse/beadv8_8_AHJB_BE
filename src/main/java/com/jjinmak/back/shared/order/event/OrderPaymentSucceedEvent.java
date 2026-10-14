package com.jjinmak.back.shared.order.event;

import java.time.LocalDateTime;

public record OrderPaymentSucceedEvent(
        Long orderId,
        Long auctionId,
        Long productId,
        LocalDateTime paidAt
) {
}
