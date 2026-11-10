package com.jjinmak.refund.app;

import com.jjinmak.refund.domain.RefundRetryResult;
import com.jjinmak.contracts.order.event.OrderRefundRequestedEvent;
import com.jjinmak.contracts.payment.event.WalletRefundedEvent;
import com.jjinmak.refund.in.dto.RefundDetailDto;
import com.jjinmak.contracts.refund.dto.RefundDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefundFacade {

    private final RefundCreateRefundUseCase refundCreateRefundUseCase;
    private final RefundGetRefundUseCase refundGetRefundUseCase;
    private final RefundRejectRefundUseCase refundRejectRefundUseCase;
    private final RefundAcceptRefundUseCase refundAcceptRefundUseCase;
    private final RefundGetRefundByOrderUseCase refundGetRefundByOrderUseCase;
    private final RefundCompleteRefundUseCase refundCompleteRefundUseCase;
    private final RefundRetryRefundUseCase refundRetryRefundUseCase;

    @Transactional
    public void createRefund(OrderRefundRequestedEvent event) {
        refundCreateRefundUseCase.createRefund(event);
    }

    @Transactional(readOnly = true)
    public RefundDetailDto getRefund(Long memberId, Long refundId) {
        return refundGetRefundUseCase.getRefund(memberId, refundId).detailDto();
    }

    @Transactional
    public RefundDto rejectRefund(Long memberId, Long refundId) {
        return refundRejectRefundUseCase.rejectRefund(memberId, refundId).dto();
    }

    @Transactional
    public RefundDto acceptRefund(Long memberId, Long refundId) {
        return refundAcceptRefundUseCase.acceptRefund(memberId, refundId).dto();
    }

    @Transactional(readOnly = true)
    public RefundDetailDto getRefundByOrder(Long memberId, Long orderId) {
        return refundGetRefundByOrderUseCase.getRefundByOrder(memberId, orderId).detailDto();
    }

    @Transactional
    public void completeRefund(WalletRefundedEvent event) {
        refundCompleteRefundUseCase.completeRefund(event);
    }

    @Transactional(readOnly = true)
    public List<Long> findRetryTargetIds(LocalDateTime before) {
        return refundRetryRefundUseCase.findRetryTargetIds(before);
    }

    @Transactional
    public RefundRetryResult retryRefund(Long refundId) {
        return refundRetryRefundUseCase.retryRefund(refundId);
    }
}
