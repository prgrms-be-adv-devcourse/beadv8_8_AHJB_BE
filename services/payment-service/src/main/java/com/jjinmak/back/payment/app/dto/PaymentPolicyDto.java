package com.jjinmak.back.payment.app.dto;

/**
 * 화면에서 안내 · 입력 검증에 쓰는 결제 정책 값. 모두 설정값(wallet.*)이다.
 *
 * - chargeMinAmount / chargeMaxAmount : 예치금 1회 충전 금액 범위. 주문 결제 중 부족분 충전도 최소 금액보다 작으면 최소 금액으로 올린다
 * - pointEarnRatePercent : 포인트 적립률(%). 구매 확정 시 예치금 · 계좌이체 결제액(배송비 제외)에 적용한다
 * - feeRatePercent       : 판매 수수료율(%). 낙찰가에 적용한다
 */
public record PaymentPolicyDto(
        long chargeMinAmount,
        long chargeMaxAmount,
        int pointEarnRatePercent,
        int feeRatePercent
) {
}
