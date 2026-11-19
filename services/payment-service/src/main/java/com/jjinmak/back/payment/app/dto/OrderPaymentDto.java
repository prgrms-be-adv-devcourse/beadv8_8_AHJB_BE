package com.jjinmak.back.payment.app.dto;

import com.jjinmak.back.payment.domain.Holding;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.enums.HoldingStatus;
import com.jjinmak.back.payment.domain.enums.PayMethod;

import java.time.LocalDateTime;

/**
 * 주문 1건의 결제 정보. 주문 상세 화면에서 "이 주문 대금을 무엇으로 얼마씩 냈고, 포인트가 얼마 적립되는가"를 보여주는 데 쓴다.
 * 구매자에게만 내려준다 (구매자의 결제수단은 판매자가 알 필요가 없다).
 *
 * - paymentId / orderGroupId : 이 주문이 속한 결제(주문 묶음). 결제 상세로 넘어갈 때 쓴다
 * - payMethod / pgMethod     : 고른 결제수단 / 직접 결제가 실제로 승인된 수단. 뜻은 PaymentSummaryDto 와 같다
 * - amount                   : 이 주문의 결제 금액 = winningPrice + deliveryFee
 * - pointAmount / depositAmount / pgAmount : 이 주문 대금을 포인트 · 예치금 · 직접 결제로 각각 얼마씩 냈는지 (합 == amount).
 *                              환불하면 이 구성 그대로 돌아간다
 * - expectedEarnPoint        : 구매 확정하면 적립될 예상 포인트. 아직 확정 전(HELD)일 때만 값이 있고 그 밖에는 0
 * - earnedPoint              : 구매 확정으로 실제 적립된 포인트. 확정 전이거나 적립 대상 금액이 없으면 0
 * - status                   : 홀딩 상태 (HELD 보관 중 / CONFIRMED 구매 확정 / RELEASED 정산 완료 / REFUNDED 환불 완료)
 */
public record OrderPaymentDto(
        Long paymentId,
        Long orderGroupId,
        Long orderId,
        PayMethod payMethod,
        String pgMethod,
        long winningPrice,
        long deliveryFee,
        long amount,
        long pointAmount,
        long depositAmount,
        long pgAmount,
        long expectedEarnPoint,
        long earnedPoint,
        HoldingStatus status,
        LocalDateTime heldAt,
        LocalDateTime confirmedAt,
        LocalDateTime refundedAt
) {
    public static OrderPaymentDto of(Payment payment, Holding holding, long expectedEarnPoint) {
        return new OrderPaymentDto(
                payment.getId(),
                payment.getOrderGroupId(),
                holding.getOrderId(),
                payment.getPayMethod(),
                payment.getPgMethod(),
                holding.getWinningPrice(),
                holding.getDeliveryFee(),
                holding.getAmount(),
                holding.getPointAmount(),
                holding.getDepositAmount(),
                holding.getPgAmount(),
                expectedEarnPoint,
                holding.getEarnedPoint(),
                holding.getStatus(),
                holding.getCreatedAt(),
                holding.getConfirmedAt(),
                holding.getRefundedAt()
        );
    }
}
