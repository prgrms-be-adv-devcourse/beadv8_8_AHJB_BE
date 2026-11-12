package com.jjinmak.payment.domain;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class HoldingPolicy {

    private final int feeRatePercent;

    public HoldingPolicy(@Value("${wallet.fee-rate-percent:10}") int feeRatePercent) {
        if (feeRatePercent < 0 || feeRatePercent > 100) {
            throw new IllegalArgumentException("수수료율은 0~100 사이여야 합니다: " + feeRatePercent);
        }
        this.feeRatePercent = feeRatePercent;
    }

    // 수수료 = 낙찰가 × 수수료율. 원 단위 절사.
    public long feeOf(long winningPrice) {
        return winningPrice * feeRatePercent / 100;
    }
}
