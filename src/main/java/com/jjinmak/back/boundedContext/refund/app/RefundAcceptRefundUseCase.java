package com.jjinmak.back.boundedContext.refund.app;

import com.jjinmak.back.boundedContext.refund.domain.Refund;
import com.jjinmak.back.boundedContext.refund.domain.RefundErrorCode;
import com.jjinmak.back.boundedContext.refund.out.RefundRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.refund.event.RefundAcceptedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundAcceptRefundUseCase {

    private final RefundRepository refundRepository;
    private final EventPublisher eventPublisher;

    /**
     * 판매자가 환불 요청을 수락한다 (ACCEPTED). 수락 이벤트로 지갑에 환불을 요청하고,
     * 지갑 환불이 끝나면 WalletRefundedEvent를 받아 COMPLETED가 된다.
     * 수락·거절이 동시에 들어오면 먼저 저장된 쪽만 성공한다 (낙관적 락).
     *
     * @param memberId - 수락하는 회원 ID (판매자여야 함)
     * @param refundId - 환불 ID
     * @return Refund - 수락된 환불 (ACCEPTED)
     */
    public Refund acceptRefund(Long memberId, Long refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(RefundErrorCode.REFUND_NOT_FOUND));

        if (!refund.isSeller(memberId)) {
            throw new BusinessException(RefundErrorCode.NOT_REFUND_SELLER);
        }

        refund.accept();

        try {
            refundRepository.flush();
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(RefundErrorCode.ALREADY_PROCESSED);
        }

        eventPublisher.publish(new RefundAcceptedEvent(refund.dto()));

        return refund;
    }
}
