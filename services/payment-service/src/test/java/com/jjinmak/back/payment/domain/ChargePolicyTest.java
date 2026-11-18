package com.jjinmak.back.payment.domain;

import com.jjinmak.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.CHARGE_AMOUNT_OUT_OF_RANGE;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.CHARGE_LIMIT_EXCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 충전 금액 정책: 최소 10,000 / 1회 최대 2,000,000 기준.
 */
class ChargePolicyTest {

    private final ChargePolicy policy = new ChargePolicy(10_000, 2_000_000);

    @Test
    @DisplayName("수동 충전은 최소 · 최대 금액을 포함한 범위 안에서만 받는다")
    void manualAmountRange() {
        assertThatCode(() -> policy.validateManualAmount(10_000)).doesNotThrowAnyException();
        assertThatCode(() -> policy.validateManualAmount(2_000_000)).doesNotThrowAnyException();

        assertThatThrownBy(() -> policy.validateManualAmount(9_999))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(CHARGE_AMOUNT_OUT_OF_RANGE);
        assertThatThrownBy(() -> policy.validateManualAmount(2_000_001))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(CHARGE_AMOUNT_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("부족분이 없으면 충전하지 않는다")
    void noShortageNoCharge() {
        assertThat(policy.chargeAmountFor(0)).isZero();
        assertThat(policy.chargeAmountFor(-500)).isZero();
    }

    @Test
    @DisplayName("부족분이 최소 충전 금액보다 작으면 최소 충전 금액으로 올린다")
    void smallShortageIsRaisedToMinimum() {
        assertThat(policy.chargeAmountFor(1)).isEqualTo(10_000);
        assertThat(policy.chargeAmountFor(300)).isEqualTo(10_000);
        assertThat(policy.chargeAmountFor(9_999)).isEqualTo(10_000);
    }

    @Test
    @DisplayName("부족분이 최소 충전 금액 이상이면 올리지 않고 부족분만 충전한다")
    void shortageAtOrOverMinimumIsKept() {
        assertThat(policy.chargeAmountFor(10_000)).isEqualTo(10_000);
        assertThat(policy.chargeAmountFor(23_000)).isEqualTo(23_000);
    }

    @Test
    @DisplayName("충전 금액이 1회 한도를 넘으면 충전할 수 없다. 한도와 같으면 된다")
    void chargeLimit() {
        assertThat(policy.exceedsLimit(2_000_000)).isFalse();
        assertThat(policy.exceedsLimit(2_000_001)).isTrue();

        assertThatCode(() -> policy.validateChargeable(2_000_000)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validateChargeable(2_000_001))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(CHARGE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("최소 금액이 0 이하이거나 최대 금액이 최소보다 작으면 정책을 만들 수 없다")
    void invalidRange() {
        assertThatThrownBy(() -> new ChargePolicy(0, 2_000_000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChargePolicy(10_000, 9_999)).isInstanceOf(IllegalArgumentException.class);
    }
}
