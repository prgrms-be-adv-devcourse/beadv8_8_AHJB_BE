package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.payment.app.dto.OrderPaymentDto;
import com.jjinmak.back.payment.app.support.PointEarnCalculator;
import com.jjinmak.back.payment.domain.Holding;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.out.repository.HoldingRepository;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import com.jjinmak.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_FORBIDDEN;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_NOT_FOUND;
import static com.jjinmak.back.payment.exception.WalletErrorCode.HOLDING_NOT_FOUND;

/**
 * 주문 1건의 결제 정보 조회. 주문 상세 화면에서 주문 id 만으로 결제 구성(포인트 · 예치금 · 직접 결제)과 적립 포인트를 보여주기 위한 조회.
 *
 * 홀딩은 결제가 끝나야 생기므로 결제 대기 중인 주문은 HOLDING_NOT_FOUND 다.
 * 구매자 본인만 볼 수 있다. 판매자를 포함해 다른 회원이 조회하면 PAYMENT_FORBIDDEN (구매자의 결제수단은 판매자가 알 필요가 없다).
 */
@Service
@RequiredArgsConstructor
public class PaymentGetOrderPaymentUseCase {

    private final HoldingRepository holdingRepository;
    private final PaymentRepository paymentRepository;
    private final PointEarnCalculator pointEarnCalculator;

    public OrderPaymentDto get(Long holderId, Long orderId) {
        Holding holding = holdingRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(HOLDING_NOT_FOUND));
        Payment payment = paymentRepository.findById(holding.getPaymentId())
                .orElseThrow(() -> new BusinessException(PAYMENT_NOT_FOUND));

        if (!payment.getHolderId().equals(holderId)) {
            throw new BusinessException(PAYMENT_FORBIDDEN);
        }
        return OrderPaymentDto.of(payment, holding, pointEarnCalculator.expectedEarnOf(holding, payment));
    }
}
