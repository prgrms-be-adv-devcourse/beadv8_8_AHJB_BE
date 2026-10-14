package com.jjinmak.back.shared.wallet.event;

import java.time.LocalDateTime;

public record WalletReleasedEvent(
        Long orderId,
        Long sellerId,
        long amount,             // 홀딩 총액 (낙찰가 + 배송비)
        long feeAmount,
        long settlementAmount,   // amount - feeAmount, 판매자 실지급액
        LocalDateTime releasedAt
) {
}
