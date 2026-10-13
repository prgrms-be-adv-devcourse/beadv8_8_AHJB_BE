package com.jjinmak.back.boundedContext.refund.app;

import com.jjinmak.back.boundedContext.refund.domain.Refund;
import com.jjinmak.back.boundedContext.refund.domain.RefundErrorCode;
import com.jjinmak.back.boundedContext.refund.out.RefundRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.refund.event.RefundRejectedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundRejectRefundUseCase {

    private final RefundRepository refundRepository;
    private final EventPublisher eventPublisher;

    /**
     * 판매자가 환불 요청을 거절한다. 거절 후 주문 도메인에 알려 구매확정 처리되게 한다.
     * 수락·거절이 동시에 들어오면 먼저 저장된 쪽만 성공한다 (낙관적 락).
     * @param memberId - 거절하는 회원 ID (판매자여야 함)
     * @param refundId - 환불 ID
     * @return Refund - 거절된 환불 (REJECTED)
     */
    public Refund rejectRefund(Long memberId, Long refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(RefundErrorCode.REFUND_NOT_FOUND));

        if (!refund.isSeller(memberId)) {
            throw new BusinessException(RefundErrorCode.NOT_REFUND_SELLER);
        }

        refund.reject();

        try {
            refundRepository.flush();
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(RefundErrorCode.ALREADY_PROCESSED);
        }

        eventPublisher.publish(new RefundRejectedEvent(refund.dto()));

        return refund;
    }
}
