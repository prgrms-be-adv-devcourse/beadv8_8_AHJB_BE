package com.jjinmak.back.boundedContext.refund.app;

import com.jjinmak.back.boundedContext.refund.domain.Refund;
import com.jjinmak.back.boundedContext.refund.domain.RefundErrorCode;
import com.jjinmak.back.boundedContext.refund.out.RefundRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.order.event.OrderRefundRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundCreateRefundUseCase {

    private final RefundRepository refundRepository;

    /**
     * 주문의 환불 요청 이벤트를 받아 환불을 생성한다.
     * 요청 가능 여부(본인 주문, 배송완료 상태)는 주문 도메인이 이미 검증했다.
     * @param event - 주문 ID, 낙찰자 ID, 판매자 ID, 상품 ID, 낙찰가
     * @return Refund - 생성된 환불 (REQUESTED)
     */
    public Refund createRefund(OrderRefundRequestedEvent event) {
        if (refundRepository.existsByOrderId(event.orderId())) {
            throw new BusinessException(RefundErrorCode.ALREADY_REQUESTED);
        }

        Refund refund = new Refund(
                event.orderId(),
                event.winnerId(),
                event.sellerId(),
                event.productId(),
                event.winningPrice()
        );

        return refundRepository.save(refund);
    }
}
