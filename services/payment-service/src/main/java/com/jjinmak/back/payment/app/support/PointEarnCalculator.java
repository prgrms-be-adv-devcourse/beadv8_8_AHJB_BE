package com.jjinmak.back.payment.app.support;

import com.jjinmak.back.payment.domain.Holding;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.PointPolicy;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PointEarnCalculator {

    private final PointPolicy pointPolicy;
    private final PaymentRepository paymentRepository;

    public long earnOf(Holding holding) {
        long cashAmount = holding.getDepositAmount();
        if (holding.getPgAmount() > 0 && isPaidByTransfer(holding.getPaymentId())) {
            cashAmount += holding.getPgAmount();
        }
        return pointPolicy.earnOf(cashAmount, holding.getWinningPrice(), holding.getDeliveryFee());
    }

    // 결제 기록이 없으면 실제 수단을 알 수 없으므로 대상에서 뺀다
    private boolean isPaidByTransfer(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .map(Payment::isPaidByTransfer)
                .orElse(false);
    }
}
