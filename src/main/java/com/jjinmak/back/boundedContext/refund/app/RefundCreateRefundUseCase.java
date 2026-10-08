package com.jjinmak.back.boundedContext.refund.app;

import com.jjinmak.back.boundedContext.refund.domain.Refund;
import com.jjinmak.back.boundedContext.refund.domain.RefundErrorCode;
import com.jjinmak.back.boundedContext.refund.domain.RefundOrder;
import com.jjinmak.back.boundedContext.refund.domain.RefundReason;
import com.jjinmak.back.boundedContext.refund.out.RefundOrderRepository;
import com.jjinmak.back.boundedContext.refund.out.RefundRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RefundCreateRefundUseCase {

    private final RefundOrderRepository refundOrderRepository;
    private final RefundRepository refundRepository;

    /**
     * 낙찰자의 환불 요청을 검증하고 환불을 생성한다.
     * @param memberId - 요청한 회원 ID (로그인 회원)
     * @param orderId - 환불할 주문 ID
     * @param reason - 환불 사유
     * @param detail - 사유 상세
     * @return Refund - 생성된 환불 (REQUESTED)
     */
    public Refund createRefund(Long memberId, Long orderId, RefundReason reason, String detail) {
        if (reason == RefundReason.OTHER && (detail == null) || detail.isEmpty()) {
            throw new BusinessException(RefundErrorCode.DETAIL_REQUIRED);
        }
        RefundOrder refundOrder = refundOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(RefundErrorCode.ORDER_NOT_FOUND));

        if (!refundOrder.isWinner(memberId)) {
            throw new BusinessException(RefundErrorCode.NOT_ORDER_WINNER);
        }

        if (!refundOrder.isRefundable()) {
            throw new BusinessException(RefundErrorCode.NOT_REFUNDABLE);
        }

        if (refundRepository.existsByRefundOrder(refundOrder)) {
            throw new BusinessException(RefundErrorCode.ALREADY_REQUESTED);
        }

        Refund refund = new Refund(refundOrder, reason, detail, LocalDateTime.now());
        return refundRepository.save(refund);
    }
}
