package com.jjinmak.back.payment.app.support;

import com.jjinmak.back.payment.domain.Holding;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.PointPolicy;
import com.jjinmak.back.payment.domain.enums.HoldingStatus;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PointEarnCalculator {

    private final PointPolicy pointPolicy;
    private final PaymentRepository paymentRepository;

    public long earnOf(Holding holding) {
        boolean paidByTransfer = holding.getPgAmount() > 0 && isPaidByTransfer(holding.getPaymentId());
        return earnOf(holding, paidByTransfer);
    }

    // 조회용: 구매 확정하면 적립될 예상 포인트. 구매 확정 때와 같은 계산이라 실제 적립액과 어긋나지 않는다.
    // 아직 확정 전(HELD)인 홀딩에만 있고, 확정 · 정산 · 환불된 홀딩은 0 이다. payment 는 이 홀딩을 만든 결제
    public long expectedEarnOf(Holding holding, Payment payment) {
        if (holding.getStatus() != HoldingStatus.HELD) {
            return 0L;
        }
        return earnOf(holding, payment.isPaidByTransfer());
    }

    private long earnOf(Holding holding, boolean paidByTransfer) {
        long cashAmount = holding.getDepositAmount();
        if (paidByTransfer) {
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
