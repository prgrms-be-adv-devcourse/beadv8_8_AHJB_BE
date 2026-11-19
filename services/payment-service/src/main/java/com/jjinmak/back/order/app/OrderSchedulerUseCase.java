package com.jjinmak.back.order.app;

import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.order.out.payment.PaymentRequest;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.order.event.*;
import com.jjinmak.back.order.domain.CartItem;
import com.jjinmak.back.order.domain.Order;
import com.jjinmak.back.order.domain.OrderGroup;
import com.jjinmak.back.order.out.repository.CartItemRepository;
import com.jjinmak.back.order.out.repository.OrderGroupRepository;
import com.jjinmak.back.order.out.repository.OrderRepository;
import com.jjinmak.contracts.payment.dto.PaymentCloseResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.jjinmak.back.order.domain.OrderState.*;
import static com.jjinmak.back.order.exception.OrderErrorCode.CART_ITEM_NOT_FOUND;
import static com.jjinmak.back.order.exception.OrderErrorCode.ORDER_STATE_BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class OrderSchedulerUseCase {

    private final EventPublisher eventPublisher;

    private final PaymentRequest paymentRequest;
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

    public List<Long> findOverWaitingGroupIds(){
        return orderRepository.findOverWaitedAll(LocalDateTime.now()).stream()
                .map(order -> order.getGroup().getId())
                .distinct()
                .toList();
    }

    public void deleteOverWaitingGroup(Long orderGroupId){
        OrderGroup group = orderSupport.getOrderGroup(orderGroupId);

        PaymentCloseResultDto result = paymentRequest.closePayment(orderGroupId, group.getWinner().getId());

        if (!result.canDiscardOrderGroup())
            throw new BusinessException(ORDER_STATE_BAD_REQUEST);

        eventPublisher.publish(new OrderGroupDeletedEvent(group.getId(), group.getTotalPrice()));

        orderGroupRepository.delete(group);
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
