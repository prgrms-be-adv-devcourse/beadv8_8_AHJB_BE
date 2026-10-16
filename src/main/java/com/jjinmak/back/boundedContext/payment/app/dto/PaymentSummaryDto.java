package com.jjinmak.back.boundedContext.payment.app.dto;

import com.jjinmak.back.boundedContext.payment.domain.Payment;
import com.jjinmak.back.boundedContext.payment.domain.enums.PaymentStatus;

import java.time.LocalDateTime;

/**
 * 결제 내역 목록의 한 줄. 주문 묶음(OrderGroup) 단위.
 * - walletUsedAmount : 예치금에서 나간 금액 = 총액 − 부족분 충전액
 * - chargeAmount     : 토스로 결제한 부족분. 0 이면 전액 예치금 결제
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
                payment.getTotalPrice() - payment.getChargeAmount(),
                payment.getChargeAmount(),
                payment.getItems().size(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getPaidAt()
        );
    }
}
