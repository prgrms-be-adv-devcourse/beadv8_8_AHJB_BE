package com.jjinmak.back.payment.domain;

import com.jjinmak.common.exception.BusinessException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.CHARGE_AMOUNT_OUT_OF_RANGE;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.CHARGE_LIMIT_EXCEEDED;

/**
 * 예치금 충전 금액 정책. 충전은 계좌이체로만 받으므로 금액 범위도 계좌이체 기준이다.
 *
 * - minAmount : 최소 충전 금액. 계좌이체 수수료가 건당 최저 금액이 있어 소액 충전은 수수료 비율이 높다
 * - maxAmount : 1회 최대 충전 금액. 계좌이체 1회 이체 한도
 *
 * 수동 충전은 이 범위 안의 금액만 받는다.
 * 주문 결제 중 부족분 충전은 부족분이 최소 금액보다 작으면 최소 금액으로 올려 충전하고, 차액은 예치금에 남긴다.
 */
@Component
@Getter
public class ChargePolicy {

    private final long minAmount;
    private final long maxAmount;

    public ChargePolicy(@Value("${wallet.charge.min-amount:10000}") long minAmount,
                        @Value("${wallet.charge.max-amount:2000000}") long maxAmount) {
        if (minAmount <= 0 || maxAmount < minAmount) {
            throw new IllegalArgumentException("충전 금액 범위가 올바르지 않습니다: min=" + minAmount + ", max=" + maxAmount);
        }
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
    }

    // 수동 충전 금액 검증
    public void validateManualAmount(long amount) {
        if (amount < minAmount || amount > maxAmount) {
            throw new BusinessException(CHARGE_AMOUNT_OUT_OF_RANGE);
        }
    }


    // 부족분을 메우려면 실제로 얼마를 충전해야 하는지. 부족분이 없으면 0.
    // 한도 검증은 하지 않는다 (견적처럼 안내만 하는 곳에서도 쓰기 위해).
    public long chargeAmountFor(long shortage) {
        if (shortage <= 0) return 0L;
        return Math.max(shortage, minAmount);
    }

    public boolean exceedsLimit(long chargeAmount) {
        return chargeAmount > maxAmount;
    }

    // 부족분 충전 금액이 1회 한도를 넘으면 결제창을 띄우기 전에 막는다
    public void validateChargeable(long chargeAmount) {
        if (exceedsLimit(chargeAmount)) {
            throw new BusinessException(CHARGE_LIMIT_EXCEEDED);
        }
    }
}
