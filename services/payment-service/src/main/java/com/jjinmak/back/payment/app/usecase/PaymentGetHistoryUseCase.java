package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.app.dto.PageDto;
import com.jjinmak.back.payment.app.dto.PaymentDetailDto;
import com.jjinmak.back.payment.app.dto.PaymentSummaryDto;
import com.jjinmak.back.payment.app.support.PointEarnCalculator;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.PaymentOrderItem;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.HoldingRepository;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class PaymentGetHistoryUseCase {

    private final PaymentRepository paymentRepository;
    private final ChargeRepository chargeRepository;
    private final HoldingRepository holdingRepository;
    private final PointEarnCalculator pointEarnCalculator;

    public PageDto<PaymentSummaryDto> list(Long holderId, int page, int size) {
        return PageDto.of(paymentRepository.findByHolderId(holderId, PageDto.latest(page, size))
                .map(PaymentSummaryDto::from));
    }

    public PaymentDetailDto detail(Long holderId, Long paymentId) {
        Payment payment = paymentRepository.findWithItemsByIdAndHolderId(paymentId, holderId)
                .orElseThrow(() -> new BusinessException(PAYMENT_NOT_FOUND));
        return toDetail(payment);
    }

    // 주문 그룹 id 로 찾는 결제 상세. 결제창 없이 바로 끝난 결제는 결제 요청 응답에 paymentId 가 없어 주문 그룹 id 로 찾는다
    public PaymentDetailDto detailByOrderGroup(Long holderId, Long orderGroupId) {
        Payment payment = paymentRepository.findByOrderGroupId(orderGroupId)
                .filter(found -> found.getHolderId().equals(holderId))
                .orElseThrow(() -> new BusinessException(PAYMENT_NOT_FOUND));
        return toDetail(payment);
    }

    private PaymentDetailDto toDetail(Payment payment) {
        List<Charge> charges = chargeRepository.findByPaymentOrderByIdDesc(payment);
        return PaymentDetailDto.of(payment, charges, expectedEarnPoint(payment));
    }

    // 아직 구매 확정하지 않은 주문을 확정하면 적립될 예상 포인트의 합. 홀딩은 결제가 끝나야 생기므로 그 전에는 0
    private long expectedEarnPoint(Payment payment) {
        if (!payment.isPaid()) {
            return 0L;
        }
        List<Long> orderIds = payment.getItems().stream().map(PaymentOrderItem::getOrderId).toList();
        return holdingRepository.findByOrderIdIn(orderIds).stream()
                .mapToLong(holding -> pointEarnCalculator.expectedEarnOf(holding, payment))
                .sum();
    }
}
