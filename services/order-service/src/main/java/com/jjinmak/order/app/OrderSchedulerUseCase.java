package com.jjinmak.order.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.order.event.*;
import com.jjinmak.order.domain.CartItem;
import com.jjinmak.order.out.eventPublisher.EventPublisher;
import com.jjinmak.order.domain.Order;
import com.jjinmak.order.domain.OrderGroup;
import com.jjinmak.order.out.repository.CartItemRepository;
import com.jjinmak.order.out.repository.OrderGroupRepository;
import com.jjinmak.order.out.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.jjinmak.order.domain.OrderState.*;
import static com.jjinmak.order.exception.OrderErrorCode.CART_ITEM_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class OrderSchedulerUseCase {

    private final EventPublisher eventPublisher;

    private final OrderRepository orderRepository;
    private final OrderGroupRepository orderGroupRepository;
    private final OrderSupport orderSupport;
    private final CartItemRepository cartItemRepository;

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

    public List<Long> findPaymentDueSoonItemIds(){
        return cartItemRepository.findPaymentDueSoonItemIds(LocalDateTime.now().plusHours(2));
    }

    public void sendPaymentDueSoonReminder(Long itemId){
        CartItem item = cartItemRepository.findByIdWithParticipant(itemId)
                .orElseThrow(() -> new BusinessException(CART_ITEM_NOT_FOUND));

        OrderRemindPaymentEvent event = new OrderRemindPaymentEvent(
                item.getId(), item.getWinner().getId(),
                item.getWinningPrice(), item.getDeliveryFee(),
                item.getPaymentDueAt()
        );

        item.sendPaymentReminder();

        eventPublisher.publish(event);
    }

    public List<Long> findExpiredSoonOrderIds(){
        return orderRepository.findExpiredSoonOrderIds(LocalDateTime.now().plusDays(1));
    }

    public void sendAutoConfirmReminder(Long orderId){
        Order order = orderSupport.getOrderWithParticipants(orderId);

        OrderRemindAutoConfirmEvent event = new OrderRemindAutoConfirmEvent(
                order.getId(),
                order.getWinner().getId(),
                order.getConfirmExpiredAt()
        );

        order.sendAutoConfirmReminder();

        eventPublisher.publish(event);
    }
}
