package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.app.dto.PageDto;
import com.jjinmak.back.boundedContext.payment.app.dto.PaymentDetailDto;
import com.jjinmak.back.boundedContext.payment.app.dto.PaymentSummaryDto;
import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.domain.Payment;
import com.jjinmak.back.boundedContext.payment.out.ChargeRepository;
import com.jjinmak.back.boundedContext.payment.out.PaymentRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.jjinmak.back.boundedContext.payment.exception.PaymentErrorCode.PAYMENT_NOT_FOUND;

/**
 * 내 결제 내역(주문 묶음 단위) 목록과 상세. 소유자가 아닌 결제는 없는 것으로 취급한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentGetHistoryUseCase {

    private final PaymentRepository paymentRepository;
    private final ChargeRepository chargeRepository;

    public PageDto<PaymentSummaryDto> list(Long holderId, int page, int size) {
        return PageDto.of(paymentRepository.findByHolderId(holderId, PageDto.latest(page, size))
                .map(PaymentSummaryDto::from));
    }

    public PaymentDetailDto detail(Long holderId, Long paymentId) {
        Payment payment = paymentRepository.findWithItemsByIdAndHolderId(paymentId, holderId)
                .orElseThrow(() -> new BusinessException(PAYMENT_NOT_FOUND));

        List<Charge> charges = chargeRepository.findByPaymentOrderByIdDesc(payment);
        return PaymentDetailDto.of(payment, charges);
    }
}
