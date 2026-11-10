package com.jjinmak.refund.app;

import com.jjinmak.refund.domain.Refund;
import com.jjinmak.refund.exception.RefundErrorCode;
import com.jjinmak.refund.out.RefundRepository;
import com.jjinmak.refund.out.eventPublisher.EventPublisher;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.event.WalletRefundedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundCompleteRefundUseCase {

    private final RefundRepository refundRepository;
    private final EventPublisher eventPublisher;

    /**
     * 지갑 환불 완료 이벤트(Kafka)를 받아 환불을 완료(COMPLETED)로 바꾼다.
     * 지갑과 다른 트랜잭션이므로 같은 메시지가 두 번 올 수 있다 → 이미 COMPLETED면 조용히 건너뛴다.
     * 실패하면 공통 설정대로 3회 재시도 후 payment.payment-refund-dlt에 보관된다.
     *
     * @param event - 환불 ID, 주문 ID, 결제 ID, 환불 금액, 환불 시각
     */
    public void completeRefund(WalletRefundedEvent event) {
        Refund refund = refundRepository.findById(event.refundId())
                .orElseThrow(() -> new BusinessException(RefundErrorCode.REFUND_NOT_FOUND));

        if (refund.isCompleted()) {
            return;
        }

        refund.complete();
    }
}
