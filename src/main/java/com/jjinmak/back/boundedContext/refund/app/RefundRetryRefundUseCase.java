package com.jjinmak.back.boundedContext.refund.app;

import com.jjinmak.back.boundedContext.refund.domain.Refund;
import com.jjinmak.back.boundedContext.refund.domain.RefundRetryResult;
import com.jjinmak.back.boundedContext.refund.out.RefundRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.shared.refund.domain.RefundStatus;
import com.jjinmak.back.shared.refund.event.RefundRetryRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefundRetryRefundUseCase {

    private final RefundRepository refundRepository;
    private final EventPublisher eventPublisher;

    /**
     * 수락(ACCEPTED)된 지 유예 시간이 지났는데 아직 지갑 환불이 끝나지 않은 환불 id를 조회한다.
     *
     * @param before - 이 시각 이전에 ACCEPTED가 된 건만 대상 (스케줄러가 지금 − 유예 시간을 넘긴다)
     * @return List<Long> - 재처리 대상 환불 id
     */
    public List<Long> findRetryTargetIds(LocalDateTime before) {
        return refundRepository.findRetryTargetIds(RefundStatus.ACCEPTED, before);
    }

    /**
     * 환불 1건의 지갑 환불을 다시 요청한다 (재처리 요청 이벤트 발행). 상태는 바꾸지 않고,
     * 지갑의 WalletRefundedEvent를 받을 때 COMPLETED가 된다.
     * 조회 이후 이미 COMPLETED가 됐으면 건너뛴다. 같은 건을 다시 요청해도 지갑이 한 번만 환불한다.
     *
     * @param refundId - 환불 ID
     * @return RefundRetryResult - REQUESTED(요청함) / SKIPPED(건너뜀)
     */
    public RefundRetryResult retryRefund(Long refundId) {
        Refund refund = refundRepository.findById(refundId).orElse(null);

        if (refund == null || !refund.isAccepted()) {
            return RefundRetryResult.SKIPPED;
        }

        eventPublisher.publish(new RefundRetryRequestedEvent(refund.dto()));

        return RefundRetryResult.REQUESTED;
    }
}