package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.OrderMemberRepository;
import com.jjinmak.back.boundedContext.order.out.OrderRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.order.event.OrderRefundRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.REFUND_REQUESTED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPED;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.*;
import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class OrderRequestRefundUseCase {

    private final OrderRepository orderRepository;
    private final OrderMemberRepository orderMemberRepository;

    public void requestRefund(UUID memberId, Long orderId){

        OrderMember member = orderMemberRepository.findByUuid(memberId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

        Order order = orderRepository.findByIdWithWinnerAndSeller(orderId)
                .orElseThrow(() -> new BusinessException(ORDER_NOT_FOUND));

        if (!order.isWinner(member)){
            throw new BusinessException(ORDER_FORBIDDEN);
        }

        if (!order.getState().equals(SHIPPED)){
            throw new BusinessException(ORDER_STATE_BAD_REQUEST);
        }

        order.updateState(REFUND_REQUESTED);

        // TODO: 환불 컨텍스트로 환불요청됨 이벤트를 발행
        OrderRefundRequestedEvent event = new OrderRefundRequestedEvent(
                orderId, member.getId(), order.getSeller().getId(),
                order.getProductId(), order.getWinningPrice()
        );
    }
}
