package com.jjinmak.back.boundedContext.payment.domain;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 홀딩 시 적용되는 금액 정책.
 *
 * - 수수료: 낙찰가의 10%. 배송비에는 붙지 않는다.
 * - 홀딩 금액: 낙찰가 + 배송비 (구매자가 실제로 지불하는 총액)
 * - 판매자 정산액: 홀딩 금액 - 수수료
 *
 * 수수료율은 wallet.fee-rate-percent 로 바꿀 수 있고, 기본값은 10 이다.
 * 정책이 바뀌어도 이미 생성된 Holding 의 feeAmount 는 홀딩 시점 값으로 고정된다.
 */
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

    /** 수수료 = 낙찰가 × 수수료율. 원 단위 절사. */
    public long feeOf(long winningPrice) {
        return winningPrice * feeRatePercent / 100;
    }
}