package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.out.OrderRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.CONFIRMED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPED;

@Service
@RequiredArgsConstructor
public class OrderSchedulerUseCase {

    private final EventPublisher eventPublisher;

    private final OrderRepository orderRepository;
    private final OrderSupport orderSupport;

    public List<Long> getExpiredOrderIds(){
        return orderRepository.findExpiredOrderIds(LocalDateTime.now());
    }

    public void confirmPurchaseAuto(Long orderId){

        Order order = orderSupport.getOrderWithParticipants(orderId);

        order.validateAndUpdateState(SHIPPED, CONFIRMED);

        OrderConfirmPurchaseEvent event = new OrderConfirmPurchaseEvent(
                orderId, order.getWinner().getId(), order.getSeller().getId(),
                order.getProductId(), order.getWinningPrice(), order.getDeliveryFee(),
                LocalDateTime.now()
        );

        eventPublisher.publish(event);
    }
}
