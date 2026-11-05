package com.jjinmak.order.app;

import com.jjinmak.order.out.eventPublisher.EventPublisher;
import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.contracts.order.event.OrderGroupDeletedEvent;
import com.jjinmak.contracts.order.event.OrderRefundRequestedEvent;
import com.jjinmak.order.domain.Order;
import com.jjinmak.order.domain.OrderGroup;
import com.jjinmak.order.out.repository.OrderGroupRepository;
import com.jjinmak.order.out.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.jjinmak.order.domain.OrderState.*;

@Service
@RequiredArgsConstructor
public class OrderSchedulerUseCase {

    private final EventPublisher eventPublisher;

    private final OrderRepository orderRepository;
    private final OrderGroupRepository orderGroupRepository;
    private final OrderSupport orderSupport;

    public List<Long> getExpiredOrderIds(){
        return orderRepository.findExpiredOrderIds(LocalDateTime.now());
    }

    public void confirmPurchaseAuto(Long orderId){

        Order order = orderSupport.getOrderWithParticipants(orderId);

        order.validateAndUpdateState(SHIPPED, CONFIRMED);

        OrderConfirmPurchaseEvent event = new OrderConfirmPurchaseEvent(
                orderId, order.getWinner().getId(), order.getSeller().getId(),
                order.getAuctionId(), order.getProductId(), order.getWinningPrice(), order.getDeliveryFee(),
                LocalDateTime.now()
        );

        eventPublisher.publish(event);
    }

    public void deleteOverWaitingOrders(){
        List<Order> orders = orderRepository.findOverWaitedAll(LocalDateTime.now());
        Set<OrderGroup> groups = orders.stream().map(Order::getGroup).collect(Collectors.toSet());

        groups.forEach(
                g -> eventPublisher.publish(new OrderGroupDeletedEvent(g.getId(), g.getTotalPrice()))
        );

        orderGroupRepository.deleteAll(groups);
    }

    public List<Long> getNonShippedOrderIds(){
        return orderRepository.findNonShippedOrderIds(LocalDateTime.now().minusDays(5));
    }

    public void requestSystemRefund(Long orderId){
        Order order = orderSupport.getOrderWithParticipants(orderId);

        order.validateAndUpdateState(PAID, REFUND_REQUESTED);

        OrderRefundRequestedEvent event = OrderRefundRequestedEvent.bySystem(
                orderId, order.getWinner().getId(), order.getSeller().getId(),
                order.getProductId(), order.getWinningPrice(), order.getDeliveryFee()
        );

        eventPublisher.publish(event);
    }
}
