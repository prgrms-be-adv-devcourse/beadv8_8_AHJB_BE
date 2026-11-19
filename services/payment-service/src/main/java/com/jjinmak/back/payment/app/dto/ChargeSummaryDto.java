package com.jjinmak.back.payment.app.dto;

import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.enums.ChargePurpose;
import com.jjinmak.back.payment.domain.enums.ChargeStatus;

import java.time.LocalDateTime;

/**
 * 충전 1건의 요약. 결제창을 띄우는 데 필요한 값(pgOrderId, amount, orderName)과 상태·표시 정보를 담는다.
 * 클라이언트는 pgOrderId 를 토스 SDK 의 orderId 로, amount 를 결제 금액으로, pgMethod 를 method 로 넘긴다.
 *
 * - pgMethod       : 결제창을 열 때 쓰는 결제수단 (TRANSFER / CARD)
 * - method         : 토스가 실제로 승인한 결제수단 (계좌이체, 카드, 간편결제 …). 승인 전에는 null
 * - canceledAmount : 직접 결제 건에서 주문 환불로 토스에 취소한 금액의 합. 충전 취소는 전액이라 취소되면 amount 와 같다
 */
public record ChargeSummaryDto(
        Long chargeId,
        String pgOrderId,
        long amount,
        String orderName,
        ChargePurpose purpose,
        ChargeStatus status,
        boolean cancelable,
        long usedAmount,
        long remainingAmount,
        long canceledAmount,
        String pgMethod,
        String method,
        String receiptUrl,
        LocalDateTime requestedAt,
        LocalDateTime approvedAt,
        LocalDateTime usedAt,
        LocalDateTime canceledAt,
        String failureCode,
        String failureMessage
) {
    public static final String MANUAL_ORDER_NAME = "찐막 예치금 충전";
    public static final String PAYMENT_ORDER_NAME = "찐막 주문 결제 (부족분 충전)";
    public static final String DIRECT_ORDER_NAME = "찐막 주문 결제";

    public static ChargeSummaryDto from(Charge charge) {
        return new ChargeSummaryDto(
                charge.getId(),
                charge.getPgOrderId(),
                charge.getAmount(),
                orderNameOf(charge),
                charge.getPurpose(),
                charge.getStatus(),
                charge.isCancelable(),
                charge.getUsedAmount(),
                charge.getRemainingAmount(),
                charge.getCanceledAmount(),
                charge.getRequiredMethod().name(),
                charge.getMethod(),
                charge.getReceiptUrl(),
                charge.getCreatedAt(),
                charge.getApprovedAt(),
                charge.getUsedAt(),
                charge.getCanceledAt(),
                charge.getFailureCode(),
                charge.getFailureMessage()
        );
    }

    private static String orderNameOf(Charge charge) {
        return switch (charge.getPurpose()) {
            case MANUAL -> MANUAL_ORDER_NAME;
            case FOR_PAYMENT -> PAYMENT_ORDER_NAME;
            case DIRECT_PAYMENT -> DIRECT_ORDER_NAME;
        };
    }
}
