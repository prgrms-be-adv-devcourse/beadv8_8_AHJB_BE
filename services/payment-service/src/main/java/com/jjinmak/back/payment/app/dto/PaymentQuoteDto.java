package com.jjinmak.back.payment.app.dto;

import com.jjinmak.back.payment.domain.PaymentPlan;
import com.jjinmak.back.payment.domain.enums.PayMethod;

/**
 * 결제 전 견적. "이 금액을 이 결제수단과 포인트 사용액으로 결제하면 무엇으로 얼마씩 나가고, 결제창에서는 얼마를 결제해야 하는가".
 *
 * - balance / pointBalance : 지금 예치금 · 포인트 잔액
 * - amount              : 결제 금액 (주문 총액)
 * - payMethod           : 결제수단 (DEPOSIT / TRANSFER / CARD)
 * - pointUseAmount      : 포인트로 내는 금액
 * - walletUseAmount     : 지금 있는 예치금으로 충당되는 금액. 직접 결제면 0
 * - shortage            : 예치금 결제에서 모자란 금액. 0 이면 충전 없이 결제된다
 * - needsCharge         : 예치금이 모자라 충전이 필요한지
 * - chargeAmount        : 실제로 충전해야 하는 금액. 부족분이 최소 충전 금액보다 작으면 최소 충전 금액으로 올린다
 * - balanceAfterPayment : 결제한 뒤 예치금에 남는 금액 (충전했다면 충전 금액 포함). 직접 결제면 지금 잔액 그대로
 * - pgAmount            : 결제창에서 결제할 금액. 충전이면 chargeAmount, 직접 결제면 포인트를 뺀 나머지. 0 이면 결제창 없이 결제된다
 * - pgMethod            : 결제창을 열어야 하는 결제수단 (TRANSFER / CARD). 결제창이 필요 없으면 null
 * - chargeLimitExceeded : 결제창 금액이 계좌이체 1회 한도를 넘는지. true 면 이 조건으로는 결제 요청이 거절된다
 *                         (예치금 결제면 예치금을 먼저 충전하고, 계좌이체 직접 결제면 다른 수단을 골라야 한다)
 * - expectedEarnPoint   : 이 조건으로 결제하고 구매 확정하면 적립될 것으로 예상되는 포인트
 *                         예치금으로 내는 금액과 계좌이체로 직접 결제하는 금액에서 배송비 몫을 뺀 뒤 적립률을 곱한다 (카드·간편결제, 포인트 사용액은 제외)
 *                         실제 적립은 구매 확정 때 주문별로 계산해 절사하므로 주문이 여러 건이면 몇 포인트 차이 날 수 있고,
 *                         계좌이체를 골랐더라도 결제창에서 다른 수단으로 결제하면 적립되지 않는다
 * - earnRatePercent     : 포인트 적립률(%)
 */
public record PaymentQuoteDto(
        long balance,
        long pointBalance,
        long amount,
        PayMethod payMethod,
        long pointUseAmount,
        long walletUseAmount,
        long shortage,
        boolean needsCharge,
        long chargeAmount,
        long balanceAfterPayment,
        long pgAmount,
        String pgMethod,
        boolean chargeLimitExceeded,
        long expectedEarnPoint,
        int earnRatePercent
) {
    public static PaymentQuoteDto of(long balance, long pointBalance, PaymentPlan plan, String pgMethod,
                                     long expectedEarnPoint, int earnRatePercent) {
        boolean direct = plan.isDirect();
        long chargeAmount = direct ? 0L : plan.chargeAmount();
        return new PaymentQuoteDto(
                balance,
                pointBalance,
                plan.totalPrice(),
                plan.payMethod(),
                plan.pointAmount(),
                plan.depositAmount() - plan.shortage(),
                plan.shortage(),
                plan.shortage() > 0,
                chargeAmount,
                balance + chargeAmount - plan.depositAmount(),
                plan.chargeAmount(),
                plan.needsPgWindow() ? pgMethod : null,
                plan.limitExceeded(),
                expectedEarnPoint,
                earnRatePercent
        );
    }
}
