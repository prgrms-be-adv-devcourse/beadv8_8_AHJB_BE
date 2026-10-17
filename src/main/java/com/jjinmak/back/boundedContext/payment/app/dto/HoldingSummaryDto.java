package com.jjinmak.back.boundedContext.payment.app.dto;

import com.jjinmak.back.boundedContext.payment.domain.Holding;
import com.jjinmak.back.boundedContext.payment.domain.enums.HoldingStatus;

import java.time.LocalDateTime;

/**
 * 홀딩 1건. 구매자에게는 "환불 가능한 돈이 어느 주문에 묶여 있나", 판매자에게는 "정산 예정 금액이 얼마인가".
 * - counterpartyId : 구매자가 보면 판매자 id, 판매자가 보면 구매자 id
 * - settlementAmount : 판매자 실지급액 = amount − feeAmount
 */
public record HoldingSummaryDto(
        Long holdingId,
        Long orderId,
        Long paymentId,
        Long counterpartyId,
        long winningPrice,
        long deliveryFee,
        long amount,
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
