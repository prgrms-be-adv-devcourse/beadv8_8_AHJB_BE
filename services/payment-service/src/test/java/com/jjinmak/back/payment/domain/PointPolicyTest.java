package com.jjinmak.back.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 포인트 적립액 계산: 현금성 결제액에서 배송비 몫을 빼고 적립률을 곱한다.
 */
class PointPolicyTest {

    private final PointPolicy policy = new PointPolicy(1);

    @Test
    @DisplayName("배송비가 없으면 현금성 결제액의 1% 를 적립한다")
    void onePercentOfCashAmount() {
        assertThat(policy.earnOf(30_000, 30_000, 0)).isEqualTo(300);
        assertThat(policy.earnOf(20_000, 30_000, 0)).isEqualTo(200);     // 나머지 10,000 은 포인트 등 현금성이 아닌 수단
    }

    @Test
    @DisplayName("배송비 몫은 적립 대상에서 뺀다: 현금성 결제액 × 낙찰가 / (낙찰가 + 배송비)")
    void deliveryFeeIsExcluded() {
        // 주문 33,000 = 낙찰가 30,000 + 배송비 3,000
        assertThat(policy.earnOf(33_000, 30_000, 3_000)).isEqualTo(300);  // 전액 현금성 → 낙찰가 몫 30,000
        assertThat(policy.earnOf(22_000, 30_000, 3_000)).isEqualTo(200);  // 22,000 × 30/33 = 20,000
    }

    @Test
    @DisplayName("원 단위로 절사한다 (대상 금액을 구할 때 한 번, 적립률을 곱할 때 한 번)")
    void roundsDown() {
        assertThat(policy.earnOf(9_999, 9_999, 0)).isEqualTo(99);
        assertThat(policy.earnOf(10_000, 30_000, 3_000)).isEqualTo(90);   // 10,000 × 30/33 = 9,090.9 → 9,090 → 90.9 → 90
        assertThat(policy.earnOf(99, 99, 0)).isZero();
    }

    @Test
    @DisplayName("현금성 결제액이 없으면 적립하지 않는다")
    void nothingForNoCash() {
        assertThat(policy.earnOf(0, 30_000, 3_000)).isZero();
        assertThat(policy.earnOf(-1, 30_000, 3_000)).isZero();
    }

    @Test
    @DisplayName("현금성 결제액이 주문 금액보다 커도 주문 금액까지만 본다")
    void cashAmountIsCappedAtOrderAmount() {
        assertThat(policy.earnOf(50_000, 30_000, 3_000)).isEqualTo(300);
    }

    @Test
    @DisplayName("적립률은 설정값을 따르고, 0 이면 적립하지 않는다")
    void rateIsConfigurable() {
        assertThat(new PointPolicy(3).earnOf(30_000, 30_000, 0)).isEqualTo(900);
        assertThat(new PointPolicy(0).earnOf(30_000, 30_000, 0)).isZero();
    }

    @Test
    @DisplayName("적립률이 0~100 을 벗어나면 만들 수 없다")
    void invalidRate() {
        assertThatThrownBy(() -> new PointPolicy(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PointPolicy(101)).isInstanceOf(IllegalArgumentException.class);
    }
}
