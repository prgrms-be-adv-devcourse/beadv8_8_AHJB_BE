package com.jjinmak.back.payment.domain;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 포인트 적립 정책. 구매 확정된 주문의 현금성 결제액에 적립률을 곱해 포인트를 준다.
 *
 * - 현금성 결제액 : 예치금으로 낸 금액 + 계좌이체로 직접 결제한 금액.
 *                  카드·간편결제로 직접 결제한 금액과 포인트로 낸 금액은 들어가지 않는다 (어느 금액이 현금성인지는 호출하는 쪽이 정한다)
 * - 배송비 제외   : 주문 금액(낙찰가 + 배송비) 중 낙찰가 비율만큼만 적립 대상으로 본다
 * - 적립률        : 현금성 결제액 전체에 같은 비율 (wallet.point.earn-rate-percent, 기본 1%)
 *
 *   적립 대상 금액 = 현금성 결제액 × 낙찰가 / (낙찰가 + 배송비)   (원 단위 절사)
 *   적립 포인트    = 적립 대상 금액 × 적립률 / 100            (원 단위 절사)
 */
@Component
@Getter
public class PointPolicy {

    private final int earnRatePercent;

    public PointPolicy(@Value("${wallet.point.earn-rate-percent:1}") int earnRatePercent) {
        if (earnRatePercent < 0 || earnRatePercent > 100) {
            throw new IllegalArgumentException("포인트 적립률은 0~100 사이여야 합니다: " + earnRatePercent);
        }
        this.earnRatePercent = earnRatePercent;
    }

    // cashAmount: 이 주문 금액(낙찰가 + 배송비) 중 현금성 수단으로 낸 금액
    public long earnOf(long cashAmount, long winningPrice, long deliveryFee) {
        long orderAmount = winningPrice + deliveryFee;
        if (cashAmount <= 0 || winningPrice <= 0 || orderAmount <= 0) {
            return 0L;
        }
        long eligible = Math.min(cashAmount, orderAmount) * winningPrice / orderAmount;
        return eligible * earnRatePercent / 100;
    }
}
