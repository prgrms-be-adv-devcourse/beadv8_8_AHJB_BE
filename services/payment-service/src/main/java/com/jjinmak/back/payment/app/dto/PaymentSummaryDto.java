package com.jjinmak.back.payment.app.dto;

import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.enums.PaymentStatus;

import java.time.LocalDateTime;

/**
 * 결제 내역 목록의 한 줄. 주문 묶음(OrderGroup) 단위.
 * - walletUsedAmount : 충전 전부터 있던 예치금에서 나간 금액 = 총액 − 충전액 (충전액이 총액 이상이면 0)
 * - chargeAmount     : 부족분을 메우려고 계좌이체로 충전한 금액. 0 이면 전액 예치금 결제. 최소 충전 금액 때문에 부족분보다 클 수 있고, 차액은 예치금에 남는다
 */
public record PaymentSummaryDto(
        Long paymentId,
        Long orderGroupId,
        long totalPrice,
        long walletUsedAmount,
        long chargeAmount,
        int orderCount,
        PaymentStatus status,
        LocalDateTime requestedAt,
        LocalDateTime paidAt
) {
    public static PaymentSummaryDto from(Payment payment) {
        return new PaymentSummaryDto(
                payment.getId(),
                payment.getOrderGroupId(),
                payment.getTotalPrice(),
                payment.getWalletUsedAmount(),
                payment.getChargeAmount(),
                payment.getItems().size(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getPaidAt()
        );
    }
}
