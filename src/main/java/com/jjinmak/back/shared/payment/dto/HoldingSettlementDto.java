package com.jjinmak.back.shared.payment.dto;

import java.time.LocalDateTime;

/**
 * 주문 1건의 정산 정보. 정산 컨텍스트가 정산 전에 금액을 미리 확인하는 용도.
 *
 * - amount           : 구매자가 지불한 총액 (낙찰가 + 배송비). ESCROW 에 묶여 있는 금액
 * - feeAmount        : 플랫폼 수수료. 홀딩 시점 정책으로 확정된 값이라 이후 정책이 바뀌어도 변하지 않는다
 * - settlementAmount : 판매자 실지급액 = amount - feeAmount
 * - status           : HELD(홀딩 중) / CONFIRMED(구매 확정, 정산 가능) / RELEASED(정산 완료) / REFUNDED(환불됨)
 */
public record HoldingSettlementDto(
        Long orderId,
        Long buyerId,
        Long sellerId,
        long winningPrice,
        long deliveryFee,
        long amount,
        long feeAmount,
        long settlementAmount,
        String status,
        LocalDateTime confirmedAt,
        LocalDateTime releasedAt
) {
    /** 구매 확정이 끝나 정산할 수 있는 상태인지 */
    public boolean isSettleable() {
        return "CONFIRMED".equals(status);
    }

    public boolean isReleased() {
        return "RELEASED".equals(status);
    }
}
