package com.jjinmak.back.payment.app.dto;

import com.jjinmak.back.payment.domain.Holding;
import com.jjinmak.back.payment.domain.enums.HoldingStatus;

import java.time.LocalDateTime;

/**
 * 홀딩 1건. 구매자에게는 "환불 가능한 돈이 어느 주문에 묶여 있나", 판매자에게는 "정산 예정 금액이 얼마인가".
 * - counterpartyId : 구매자가 보면 판매자 id, 판매자가 보면 구매자 id
 * - settlementAmount : 판매자 실지급액 = amount − feeAmount
 * - pointAmount / depositAmount / pgAmount : 이 주문 대금을 포인트 · 예치금 · 직접 결제로 각각 얼마씩 냈는지 (합 == amount).
 *   환불하면 이 구성 그대로 돌아간다. 구매자에게만 내려주고 판매자가 볼 때는 0 이다 (구매자의 결제수단은 판매자가 알 필요가 없다)
 */
public record HoldingSummaryDto(
        Long holdingId,
        Long orderId,
        Long paymentId,
        Long counterpartyId,
        long winningPrice,
        long deliveryFee,
        long amount,
        long pointAmount,
        long depositAmount,
        long pgAmount,
        long feeAmount,
        long settlementAmount,
        HoldingStatus status,
        LocalDateTime heldAt,
        LocalDateTime confirmedAt,
        LocalDateTime releasedAt,
        LocalDateTime refundedAt
) {
    public static HoldingSummaryDto from(Holding holding, HoldingRole role) {
        Long counterpartyId = role == HoldingRole.BUYER
                ? holding.getSellerWallet().getHolder().getId()
                : holding.getBuyerWallet().getHolder().getId();
        return new HoldingSummaryDto(
                holding.getId(),
                holding.getOrderId(),
                holding.getPaymentId(),
                counterpartyId,
                holding.getWinningPrice(),
                holding.getDeliveryFee(),
                holding.getAmount(),
                role == HoldingRole.BUYER ? holding.getPointAmount() : 0L,
                role == HoldingRole.BUYER ? holding.getDepositAmount() : 0L,
                role == HoldingRole.BUYER ? holding.getPgAmount() : 0L,
                holding.getFeeAmount(),
                holding.getSettlementAmount(),
                holding.getStatus(),
                holding.getCreatedAt(),
                holding.getConfirmedAt(),
                holding.getReleasedAt(),
                holding.getRefundedAt()
        );
    }
}
