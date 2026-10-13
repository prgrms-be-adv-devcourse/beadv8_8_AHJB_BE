package com.jjinmak.back.boundedContext.refund.app;

import com.jjinmak.back.shared.order.event.OrderRefundRequestedEvent;
import com.jjinmak.back.shared.refund.dto.RefundDetailDto;
import com.jjinmak.back.shared.refund.dto.RefundDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundFacade {

    private final RefundCreateRefundUseCase refundCreateRefundUseCase;
    private final RefundGetRefundUseCase refundGetRefundUseCase;
    private final RefundRejectRefundUseCase refundRejectRefundUseCase;
    private final RefundAcceptRefundUseCase refundAcceptRefundUseCase;

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

}
