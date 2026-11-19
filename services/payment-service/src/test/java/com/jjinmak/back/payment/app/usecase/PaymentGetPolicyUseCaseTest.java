package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.payment.app.dto.PaymentPolicyDto;
import com.jjinmak.back.payment.domain.ChargePolicy;
import com.jjinmak.back.payment.domain.HoldingPolicy;
import com.jjinmak.back.payment.domain.PointPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentGetPolicyUseCaseTest {

    @Test
    @DisplayName("각 정책이 읽은 설정값을 그대로 돌려준다")
    void returnsConfiguredValues() {
        PaymentGetPolicyUseCase useCase = new PaymentGetPolicyUseCase(
                new ChargePolicy(10_000, 2_000_000), new PointPolicy(1), new HoldingPolicy(10));

        PaymentPolicyDto policy = useCase.get();

        assertThat(policy.chargeMinAmount()).isEqualTo(10_000);
        assertThat(policy.chargeMaxAmount()).isEqualTo(2_000_000);
        assertThat(policy.pointEarnRatePercent()).isEqualTo(1);
        assertThat(policy.feeRatePercent()).isEqualTo(10);
    }
}
