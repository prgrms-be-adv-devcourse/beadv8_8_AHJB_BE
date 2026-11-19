package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.payment.app.dto.PaymentQuoteDto;
import com.jjinmak.back.payment.app.support.WalletSupport;
import com.jjinmak.back.payment.domain.PaymentPlan;
import com.jjinmak.back.payment.domain.PaymentPlanner;
import com.jjinmak.back.payment.domain.PointPolicy;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.enums.ChargePurpose;
import com.jjinmak.back.payment.domain.enums.PayMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.INVALID_PAYMENT_AMOUNT;

/**
 * 결제 전 견적. 장바구니 화면에서 결제수단과 포인트 사용액을 바꿔 가며
 * "포인트 n원, 예치금 m원 사용, 계좌이체로 k원 충전" 또는 "포인트 n원 사용, 카드로 k원 결제"를 미리 보여주기 위한 계산.
 *
 * 실제 결제 요청과 같은 계산(PaymentPlanner)을 쓴다. 포인트 사용액이 범위를 벗어나거나 포인트가 모자라면 결제 요청과 같은 오류를 돌려주고,
 * 한도 초과는 오류 대신 chargeLimitExceeded 로 알린다. 저장하지 않으며 실제 결제 시점에 다시 계산된다.
 */
@Service
@RequiredArgsConstructor
public class PaymentQuoteUseCase {

    private final WalletSupport walletSupport;
    private final PaymentPlanner paymentPlanner;
    private final PointPolicy pointPolicy;

    // 전액 예치금 결제 견적 (결제수단 · 포인트를 고르지 않은 경우)
    public PaymentQuoteDto quote(Long holderId, long amount) {
        return quote(holderId, amount, null, 0L, 0L);
    }

    public PaymentQuoteDto quote(Long holderId, long amount, String payMethod, long pointUseAmount) {
        return quote(holderId, amount, payMethod, pointUseAmount, 0L);
    }

    // deliveryFee: amount 에 포함된 배송비 합. 예상 적립 포인트에서 배송비 몫을 빼는 데만 쓴다
    public PaymentQuoteDto quote(Long holderId, long amount, String payMethod, long pointUseAmount, long deliveryFee) {
        if (deliveryFee < 0 || (deliveryFee > 0 && deliveryFee >= amount)) {
            throw new BusinessException(INVALID_PAYMENT_AMOUNT);
        }
        PayMethod method = PayMethod.from(payMethod);
        Wallet wallet = walletSupport.getWalletByHolderId(holderId);

        PaymentPlan plan = paymentPlanner.plan(amount, method, pointUseAmount, wallet.getBalance(), wallet.getPointBalance());

        String pgMethod = method.isDirect()
                ? method.pgMethod().name()
                : ChargePurpose.FOR_PAYMENT.requiredMethod().name();
        return PaymentQuoteDto.of(wallet.getBalance(), wallet.getPointBalance(), plan, pgMethod,
                expectedEarnPoint(plan, amount, deliveryFee), pointPolicy.getEarnRatePercent());
    }

    // 현금성 결제액 = 예치금으로 내는 금액 + (계좌이체 직접 결제면) 결제창에서 내는 금액. 카드·간편결제 직접 결제는 대상이 아니다
    private long expectedEarnPoint(PaymentPlan plan, long amount, long deliveryFee) {
        long cashAmount = plan.depositAmount();
        if (plan.payMethod() == PayMethod.TRANSFER) {
            cashAmount += plan.pgAmount();
        }
        return pointPolicy.earnOf(cashAmount, amount - deliveryFee, deliveryFee);
    }
}
