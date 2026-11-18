package com.jjinmak.back.boundedContext.refund.app;

import com.jjinmak.back.boundedContext.refund.domain.Refund;
import com.jjinmak.back.boundedContext.refund.domain.RefundErrorCode;
import com.jjinmak.back.boundedContext.refund.out.RefundRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundGetRefundByOrderUseCase {

    private final RefundRepository refundRepository;

    /**
     * 주문 ID로 그 주문의 환불을 조회한다. 주문 화면에서 refundId 없이 환불 정보를 볼 때 쓴다.
     * 해당 주문의 구매자·판매자만 볼 수 있다.
     * @param memberId - 조회하는 회원 ID (로그인 회원)
     * @param orderId - 주문 ID
     * @return Refund - 조회된 환불
     */
    public Refund getRefundByOrder(Long memberId, Long orderId){
        Refund refund = refundRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(RefundErrorCode.REFUND_NOT_FOUND));

        if (!refund.isParticipant(memberId)) {
            throw new BusinessException(RefundErrorCode.NOT_REFUND_PARTICIPANT);
        }

        return refund;
    }
}
