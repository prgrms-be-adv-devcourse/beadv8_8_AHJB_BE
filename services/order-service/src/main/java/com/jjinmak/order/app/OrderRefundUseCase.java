package com.jjinmak.order.app;

import com.jjinmak.order.out.eventPublisher.EventPublisher;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.contracts.order.event.OrderRefundAcceptedEvent;
import com.jjinmak.contracts.order.event.OrderRefundRequestedEvent;
import com.jjinmak.contracts.refund.dto.RefundDto;
import com.jjinmak.order.domain.Order;
import com.jjinmak.order.domain.OrderMember;
import com.jjinmak.order.out.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.order.domain.OrderState.*;
import static com.jjinmak.order.exception.OrderErrorCode.ORDER_NOT_FOUND;

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
