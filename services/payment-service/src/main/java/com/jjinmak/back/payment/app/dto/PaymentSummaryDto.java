package com.jjinmak.back.payment.app.dto;

import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.enums.PayMethod;
import com.jjinmak.back.payment.domain.enums.PaymentStatus;

import java.time.LocalDateTime;

/**
 * 결제 내역 목록의 한 줄. 주문 묶음(OrderGroup) 단위.
 * 총액을 무엇으로 냈는지: pointAmount + walletUsedAmount + pgAmount == totalPrice
 * - payMethod        : 고른 결제수단 (DEPOSIT / TRANSFER / CARD)
 * - pointAmount      : 포인트로 낸 금액
 * - walletUsedAmount : 예치금으로 낸 금액. 부족분을 충전해서 낸 금액도 포함한다
 * - pgAmount         : 결제창에서 직접 결제한 금액 (예치금을 거치지 않음)
 * - pgMethod         : 직접 결제가 실제로 승인된 결제수단 (계좌이체, 카드, 간편결제 …). 직접 결제가 아니거나 승인 전이면 null
 * - chargeAmount     : 결제창에서 결제한(할) 금액. 예치금 결제면 부족분을 메우려고 계좌이체로 충전한 금액이며
 *                      최소 충전 금액 때문에 부족분보다 클 수 있고 차액은 예치금에 남는다. 직접 결제면 pgAmount 와 같다. 0 이면 결제창 없이 결제
 */
public record PaymentSummaryDto(
        Long paymentId,
        Long orderGroupId,
        long totalPrice,
        PayMethod payMethod,
        long pointAmount,
        long walletUsedAmount,
        long pgAmount,
        String pgMethod,
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
                payment.getPayMethod(),
                payment.getPointAmount(),
                payment.getDepositAmount(),
                payment.getPgAmount(),
                payment.getPgMethod(),
                payment.getChargeAmount(),
                payment.getItems().size(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getPaidAt()
        );
    }
}
