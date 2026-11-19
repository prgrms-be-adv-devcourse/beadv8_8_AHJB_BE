package com.jjinmak.back.payment.domain;

import com.jjinmak.back.payment.domain.enums.PayMethod;

/**
 * 주문 결제 1건을 어떻게 낼지 정한 결과. PaymentPlanner 가 만들고, Payment 에 저장한다.
 *
 * - payMethod     : 사용자가 고른 결제수단 (포인트로 내고 남은 금액을 낼 수단)
 * - pointAmount   : 포인트로 내는 금액
 * - depositAmount : 예치금으로 내는 금액. 부족분을 충전해서 내는 금액도 여기에 포함된다
 * - pgAmount      : 결제창에서 직접 결제하는 금액. 예치금을 거치지 않는다
 *   → 세 값의 합 == 결제 총액
 *
 * - chargeAmount  : 결제창에서 결제할 금액. 0 이면 결제창 없이 바로 결제된다
 *                   예치금 결제면 부족분 충전 금액(최소 충전 금액 때문에 부족분보다 클 수 있다), 직접 결제면 pgAmount
 * - shortage      : 예치금 결제에서 예치금이 모자란 금액. 직접 결제면 0
 * - limitExceeded : 결제창 금액이 계좌이체 1회 한도를 넘는지. true 면 이 조건으로는 결제할 수 없다
 */
public record PaymentPlan(
        PayMethod payMethod,
        long pointAmount,
        long depositAmount,
        long pgAmount,
        long chargeAmount,
        long shortage,
        boolean limitExceeded
) {
    public long totalPrice() {
        return pointAmount + depositAmount + pgAmount;
    }

    public PaymentShare share() {
        return new PaymentShare(pointAmount, depositAmount, pgAmount);
    }

    // 결제창을 거쳐야 하는지
    public boolean needsPgWindow() {
        return chargeAmount > 0;
    }

    // 결제창에서 낸 돈이 예치금을 거치지 않고 주문 대금이 되는지
    public boolean isDirect() {
        return pgAmount > 0;
    }
}
