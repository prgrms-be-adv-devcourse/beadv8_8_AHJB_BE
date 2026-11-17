package com.jjinmak.back.order.app;

import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.order.domain.Order;
import com.jjinmak.back.order.domain.OrderMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.back.order.domain.OrderState.CONFIRMED;
import static com.jjinmak.back.order.domain.OrderState.SHIPPED;


@Service
@RequiredArgsConstructor
public class OrderConfirmPurchaseUseCase {

    private final OrderSupport orderSupport;
    private final EventPublisher eventPublisher;

    public void confirmPurchase(Long memberId, Long orderId){

        OrderMember winner = orderSupport.getMember(memberId);

        Order order = orderSupport.getOrderWithParticipants(orderId);

        order.validateWinner(winner);

        order.validateAndUpdateState(SHIPPED, CONFIRMED);

        OrderConfirmPurchaseEvent event = new OrderConfirmPurchaseEvent(
                orderId, winner.getId(), order.getSeller().getId(),
                order.getAuctionId(), order.getProductId(), order.getWinningPrice(), order.getDeliveryFee(),
                LocalDateTime.now()
        );

        eventPublisher.publish(event);
    }
}
