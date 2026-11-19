package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.payment.app.dto.PaymentPolicyDto;
import com.jjinmak.back.payment.domain.ChargePolicy;
import com.jjinmak.back.payment.domain.HoldingPolicy;
import com.jjinmak.back.payment.domain.PointPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 정책 조회. 화면이 충전 금액 범위 · 적립률 · 수수료율을 따로 적어 두지 않고 서버가 실제로 쓰는 값을 받아 가게 한다.
 * 값은 각 정책(ChargePolicy · PointPolicy · HoldingPolicy)이 읽은 설정값 그대로다.
 */
@Service
@RequiredArgsConstructor
public class PaymentGetPolicyUseCase {

    private final ChargePolicy chargePolicy;
    private final PointPolicy pointPolicy;
    private final HoldingPolicy holdingPolicy;

    public PaymentPolicyDto get() {
        return new PaymentPolicyDto(
                chargePolicy.getMinAmount(),
                chargePolicy.getMaxAmount(),
                pointPolicy.getEarnRatePercent(),
                holdingPolicy.getFeeRatePercent()
        );
    }
}
