package com.jjinmak.back.boundedContext.refund.app;

import com.jjinmak.back.boundedContext.refund.domain.Refund;
import com.jjinmak.back.boundedContext.refund.domain.RefundErrorCode;
import com.jjinmak.back.boundedContext.refund.out.RefundRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.order.event.OrderRefundRequestedEvent;
import com.jjinmak.back.shared.order.event.OrderRefundRequestedEvent.RefundType;
import com.jjinmak.back.shared.refund.event.RefundAcceptedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundCreateRefundUseCase {

    private final RefundRepository refundRepository;
    private final EventPublisher eventPublisher;

    /**
     * 주문의 환불 요청 이벤트를 받아 환불을 생성한다.
     * 요청 가능 여부(본인 주문, 배송완료 상태)는 주문 도메인이 이미 검증했다.
     * 환불 금액은 구매자가 결제한 금액 전체(낙찰가 + 배송비)다.
     * 낙찰자 요청(WINNER_REQUEST)은 REQUESTED로 두고 판매자 수락을 기다린다.
     * 시스템 요청(SYSTEM_REQUEST, 판매자 미발송)은 판매자 판단 없이 즉시 수락(ACCEPTED)하고
     * RefundAcceptedEvent를 발행해 지갑 환불·주문 REFUNDED 흐름을 그대로 태운다.
     *
     * @param event - 주문 ID, 낙찰자 ID, 판매자 ID, 상품 ID, 낙찰가, 배송비, 요청 주체
     * @return Refund - 생성된 환불 (REQUESTED 또는 ACCEPTED)
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
                event.winningPrice() + event.deliveryFee()
        );

        refundRepository.save(refund);

        if (event.refundType() == RefundType.SYSTEM_REQUEST) {
            refund.accept();
            eventPublisher.publish(new RefundAcceptedEvent(refund.dto()));
        }

        return refund;
    }
}