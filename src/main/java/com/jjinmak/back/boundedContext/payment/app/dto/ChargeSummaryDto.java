package com.jjinmak.back.boundedContext.payment.app.dto;

import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.domain.enums.ChargePurpose;
import com.jjinmak.back.boundedContext.payment.domain.enums.ChargeStatus;

import java.time.LocalDateTime;

/**
 * 충전 1건의 요약. 결제창을 띄우는 데 필요한 값(pgOrderId, amount, orderName)과 상태·표시 정보를 담는다.
 * 클라이언트는 pgOrderId 를 토스 SDK 의 orderId 로, amount 를 결제 금액으로 넘긴다.
 *
 * - cancelable   : 지금 취소 버튼을 보여줘도 되는지 (CHARGED 상태). 실제 취소는 잔액이 충전액 이상일 때만 성공한다
 * - receiptUrl   : 토스 매출전표 링크. 승인된 건만 있다
 * - failureCode / failureMessage : ABORTED 인 건의 실패 사유
 */
public record ChargeSummaryDto(
        Long chargeId,
        String pgOrderId,
        long amount,
        String orderName,
        ChargePurpose purpose,
        ChargeStatus status,
        boolean cancelable,
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

    public static ChargeSummaryDto from(Charge charge) {
        return new ChargeSummaryDto(
                charge.getId(),
                charge.getPgOrderId(),
                charge.getAmount(),
                charge.isForPayment() ? PAYMENT_ORDER_NAME : MANUAL_ORDER_NAME,
                charge.getPurpose(),
                charge.getStatus(),
                charge.isCancelable(),
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
}
