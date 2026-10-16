package com.jjinmak.back.boundedContext.payment.app.dto;

import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.domain.enums.ChargePurpose;
import com.jjinmak.back.boundedContext.payment.domain.enums.ChargeStatus;

import java.time.LocalDateTime;

/**
 * 충전 1건의 요약. 결제창을 띄우는 데 필요한 값(pgOrderId, amount, orderName)과 상태를 담는다.
 * 클라이언트는 pgOrderId 를 토스 SDK 의 orderId 로, amount 를 결제 금액으로 넘긴다.
 */
public record ChargeSummaryDto(
        Long chargeId,
        String pgOrderId,
        long amount,
        String orderName,
        ChargePurpose purpose,
        ChargeStatus status,
        LocalDateTime approvedAt,
        LocalDateTime canceledAt
) {
    public static final String MANUAL_ORDER_NAME = "찐막 예치금 충전";
    public static final String PAYMENT_ORDER_NAME = "찐막 주문 결제 (부족분 충전)";

    public static ChargeSummaryDto from(Charge charge) {
        return new ChargeSummaryDto(
                charge.getId(),
                charge.getPgOrderId(),
                charge.getAmount(),
                charge.isForPayment() ? PAYMENT_ORDER_NAME : MANUAL_ORDER_NAME,
                charge.getPurpose(),
                charge.getStatus(),
                charge.getApprovedAt(),
                charge.getCanceledAt()
        );
    }
}
