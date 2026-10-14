package com.jjinmak.back.shared.wallet.event;

import java.time.LocalDateTime;

public record WalletRefundedEvent(
        Long refundId,
        Long orderId,
        Long paymentId,
        long amount,
        LocalDateTime refundedAt
) {
}
