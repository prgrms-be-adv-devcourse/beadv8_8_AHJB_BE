package com.jjinmak.back.boundedContext.refund.app;

import com.jjinmak.back.boundedContext.refund.domain.RefundReason;
import com.jjinmak.back.shared.refund.dto.RefundDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundFacade {

    private final RefundCreateRefundUseCase refundCreateRefundUseCase;

    @Transactional
    public RefundDto createRefund(Long memberId, Long orderId,
                                  RefundReason reason, String detail) {
        return refundCreateRefundUseCase.createRefund(memberId, orderId, reason, detail).dto();
    }
}
