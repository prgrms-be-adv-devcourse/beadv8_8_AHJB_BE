package com.jjinmak.back.payment.domain;

import com.jjinmak.back.payment.domain.enums.PayMethod;
import com.jjinmak.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.CHARGE_LIMIT_EXCEEDED;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.INVALID_PAYMENT_AMOUNT;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_TRANSFER_LIMIT_EXCEEDED;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.POINT_USE_OUT_OF_RANGE;
import static com.jjinmak.back.payment.exception.WalletErrorCode.INSUFFICIENT_POINT;

/**
 * 결제 총액 · 결제수단 · 포인트 사용액 · 지금 잔액으로 결제 금액 구성을 정한다. 결제 요청과 결제 전 견적이 함께 쓴다.
 *
 *   포인트 사용액을 먼저 빼고, 남은 금액을 고른 수단으로 낸다.
 *     DEPOSIT         : 남은 금액을 예치금으로. 모자라면 부족분에 충전 정책(최소 충전 금액)을 적용해 계좌이체로 충전한다
 *     TRANSFER · CARD : 남은 금액을 결제창에서 직접 결제한다. 예치금은 쓰지 않는다
 *   포인트로 전액을 내면 수단과 관계없이 결제창 없이 끝난다.
 *
 * 한도: 예치금 부족분 충전과 계좌이체 직접 결제는 계좌이체 1회 한도(ChargePolicy.maxAmount)를 넘을 수 없다.
 * 견적처럼 안내만 하는 곳에서도 쓰도록 한도 초과는 예외 대신 PaymentPlan.limitExceeded 로 알리고,
 * 실제 결제 요청은 validateLimit 으로 막는다.
 */
@Component
@RequiredArgsConstructor
public class PaymentPlanner {

    private final ChargePolicy chargePolicy;

    public PaymentPlan plan(long totalPrice, PayMethod payMethod, long pointUseAmount, long balance, long pointBalance) {
        if (totalPrice <= 0) {
            throw new BusinessException(INVALID_PAYMENT_AMOUNT);
        }
        if (pointUseAmount < 0 || pointUseAmount > totalPrice) {
            throw new BusinessException(POINT_USE_OUT_OF_RANGE);
        }
        if (pointUseAmount > pointBalance) {
            throw new BusinessException(INSUFFICIENT_POINT);
        }

        long rest = totalPrice - pointUseAmount;

        if (payMethod.isDirect()) {
            boolean limitExceeded = payMethod == PayMethod.TRANSFER && chargePolicy.exceedsLimit(rest);
            return new PaymentPlan(payMethod, pointUseAmount, 0L, rest, rest, 0L, limitExceeded);
        }

        long shortage = Math.max(0L, rest - balance);
        long chargeAmount = chargePolicy.chargeAmountFor(shortage);
        return new PaymentPlan(payMethod, pointUseAmount, rest, 0L, chargeAmount, shortage, chargePolicy.exceedsLimit(chargeAmount));
    }

    // 한도를 넘는 결제는 결제창을 띄우기 전에 막는다
    public void validateLimit(PaymentPlan plan) {
        if (!plan.limitExceeded()) return;
        throw new BusinessException(plan.isDirect() ? PAYMENT_TRANSFER_LIMIT_EXCEEDED : CHARGE_LIMIT_EXCEEDED);
    }
}
