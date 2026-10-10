package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.shared.order.event.OrderRefundRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.REFUND_REQUESTED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPED;

@Service
@RequiredArgsConstructor
public class OrderRequestRefundUseCase {

    private final OrderSupport orderSupport;

    public void requestRefund(UUID memberId, Long orderId){

        OrderMember member = orderSupport.getMember(memberId);

        Order order = orderSupport.getOrderWithParticipants(orderId);

        order.validateWinner(member);

        order.validateAndUpdateState(SHIPPED, REFUND_REQUESTED);

        // TODO: 환불 컨텍스트로 환불요청됨 이벤트를 발행
        OrderRefundRequestedEvent event = new OrderRefundRequestedEvent(
                orderId, member.getId(), order.getSeller().getId(),
                order.getProductId(), order.getWinningPrice()
        );
    }
}
