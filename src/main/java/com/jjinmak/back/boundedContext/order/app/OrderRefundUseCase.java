package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.repository.OrderRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.shared.order.event.OrderRefundAcceptedEvent;
import com.jjinmak.back.shared.order.event.OrderRefundRequestedEvent;
import com.jjinmak.back.shared.refund.dto.RefundDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.*;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.CONFIRMED;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class OrderRefundUseCase {

    private final OrderSupport orderSupport;
    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;

    public void requestRefund(Long memberId, Long orderId){

        OrderMember member = orderSupport.getMember(memberId);

        Order order = orderSupport.getOrderWithParticipants(orderId);

        order.validateWinner(member);

        order.validateAndUpdateState(SHIPPED, REFUND_REQUESTED);

        OrderRefundRequestedEvent event = OrderRefundRequestedEvent.byWinner(
                orderId, member.getId(), order.getSeller().getId(),
                order.getProductId(), order.getWinningPrice(), order.getDeliveryFee()
        );

        eventPublisher.publish(event);
    }

    public void acceptRefund(RefundDto refund){
        Long orderId = refund.orderId();

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ORDER_NOT_FOUND));

        order.validateAndUpdateState(REFUND_REQUESTED, REFUNDED);

        eventPublisher.publish(new OrderRefundAcceptedEvent(orderId, order.getAuctionId()));
    }

    public void rejectRefund(RefundDto refund){
        Long orderId = refund.orderId();

        Order order = orderSupport.getOrderWithParticipants(orderId);

        order.validateAndUpdateState(REFUND_REQUESTED, CONFIRMED);

        OrderConfirmPurchaseEvent event = new OrderConfirmPurchaseEvent(
                orderId, order.getWinner().getId(), order.getSeller().getId(),
                order.getAuctionId(), order.getProductId(), order.getWinningPrice(), order.getDeliveryFee(),
                LocalDateTime.now()
        );

        eventPublisher.publish(event);
    }
}
