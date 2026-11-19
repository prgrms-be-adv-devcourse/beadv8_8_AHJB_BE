package com.jjinmak.back.payment.app.dto;

import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.PaymentOrderItem;
import com.jjinmak.back.payment.domain.enums.PayMethod;
import com.jjinmak.back.payment.domain.enums.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;


// 결제 상세. 주문별 내역과 이 결제에 연결된 토스 결제 건(부족분 충전 또는 직접 결제)을 함께 돌려준다.
// 금액 구성 필드의 뜻은 PaymentSummaryDto 와 같다 (pointAmount + walletUsedAmount + pgAmount == totalPrice).
// expectedEarnPoint 는 이 결제의 주문 중 아직 구매 확정하지 않은 주문을 확정하면 적립될 예상 포인트의 합이다.
// 결제 완료 전이거나, 모든 주문이 확정 · 환불됐으면 0 이다 (이미 적립된 포인트는 주문별 결제 정보의 earnedPoint 로 본다).
public record PaymentDetailDto(
        Long paymentId,
        Long orderGroupId,
        long totalPrice,
        PayMethod payMethod,
        long pointAmount,
        long walletUsedAmount,
        long pgAmount,
        String pgMethod,
        long chargeAmount,
        PaymentStatus status,
        LocalDateTime requestedAt,
        LocalDateTime paidAt,
        String failureReason,
        long expectedEarnPoint,
        List<Item> items,
        List<ChargeSummaryDto> charges
) {
    public record Item(
            Long orderId,
            Long sellerId,
            long winningPrice,
            long deliveryFee,
            long amount,
            boolean refunded,
            LocalDateTime refundedAt
    ) {
        static Item from(PaymentOrderItem item) {
            return new Item(item.getOrderId(), item.getSellerId(), item.getWinningPrice(), item.getDeliveryFee(),
                    item.getAmount(), item.isRefunded(), item.getRefundedAt());
        }
    }

    public static PaymentDetailDto of(Payment payment, List<Charge> charges, long expectedEarnPoint) {
        return new PaymentDetailDto(
                payment.getId(),
                payment.getOrderGroupId(),
                payment.getTotalPrice(),
                payment.getPayMethod(),
                payment.getPointAmount(),
                payment.getDepositAmount(),
                payment.getPgAmount(),
                payment.getPgMethod(),
                payment.getChargeAmount(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getPaidAt(),
                payment.getFailureReason(),
                expectedEarnPoint,
                payment.getItems().stream().map(Item::from).toList(),
                charges.stream().map(ChargeSummaryDto::from).toList()
        );
    }
}
