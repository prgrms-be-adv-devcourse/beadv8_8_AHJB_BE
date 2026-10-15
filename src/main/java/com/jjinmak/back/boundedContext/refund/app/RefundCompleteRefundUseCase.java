package com.jjinmak.back.boundedContext.refund.app;

import com.jjinmak.back.boundedContext.refund.domain.Refund;
import com.jjinmak.back.boundedContext.refund.domain.RefundErrorCode;
import com.jjinmak.back.shared.refund.event.RefundCompletedEvent;
import com.jjinmak.back.boundedContext.refund.out.RefundRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.payment.event.WalletRefundedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundCompleteRefundUseCase {

    private final RefundRepository refundRepository;
    private final EventPublisher eventPublisher;

    /**
     * 지갑 환불이 끝나면 환불을 완료(COMPLETED)로 바꾸고 환불 완료 이벤트를 발행한다.
     * 지갑 환불과 같은 트랜잭션에서 실행되므로, 여기서 실패하면 지갑 환불도 롤백된다.
     *
     * @param event - 환불 ID, 주문 ID, 결제 ID, 환불 금액, 환불 시각
     */
    public void completeRefund(WalletRefundedEvent event) {
        Refund refund = refundRepository.findById(event.refundId())
                .orElseThrow(() -> new BusinessException(RefundErrorCode.REFUND_NOT_FOUND));

        refund.complete();

        eventPublisher.publish(new RefundCompletedEvent(refund.dto()));
    }
}