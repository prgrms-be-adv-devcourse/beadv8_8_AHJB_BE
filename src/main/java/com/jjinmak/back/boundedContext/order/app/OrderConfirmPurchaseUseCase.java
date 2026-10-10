package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.CONFIRMED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPED;

@Service
@RequiredArgsConstructor
public class OrderConfirmPurchaseUseCase {

    private final OrderSupport orderSupport;

    public void confirmPurchase(UUID memberId, Long orderId){

        OrderMember winner = orderSupport.getMember(memberId);

        Order order = orderSupport.getOrderWithParticipants(orderId);

        order.validateWinner(winner);

        order.validateAndUpdateState(SHIPPED, CONFIRMED);

        // TODO: 구매 확정 이벤트 발행
        OrderConfirmPurchaseEvent event = new OrderConfirmPurchaseEvent(
                orderId, winner.getId(), order.getSeller().getId(),
                order.getProductId(), order.getWinningPrice(), order.getDeliveryFee(),
                LocalDateTime.now()
        );
    }
}
