package com.jjinmak.back.order.app;

import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.delivery.domain.CarrierType;
import com.jjinmak.contracts.delivery.domain.DeliveryStatusCode;
import com.jjinmak.contracts.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.contracts.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.contracts.order.event.OrderSendProductEvent;
import com.jjinmak.back.order.domain.Order;
import com.jjinmak.back.order.domain.OrderMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumSet;

import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.EXCEPTION;
import static com.jjinmak.back.order.domain.OrderState.*;
import static com.jjinmak.back.order.exception.OrderErrorCode.ORDER_STATE_BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class OrderDeliveryUseCase {

    private final OrderSupport orderSupport;
    private final EventPublisher eventPublisher;

    public void requestDelivery(Long sellerId, Long orderId, CarrierType carrier, String waybill){
        OrderMember seller = orderSupport.getMember(sellerId);

        Order order = orderSupport.getOrderWithParticipants(orderId);

        order.validateSeller(seller);
        order.validateState(PAID);

        OrderSendProductEvent event = new OrderSendProductEvent(
                orderId, order.getWinner().getId(), order.getSeller().getId(),
                carrier, waybill
        );

        eventPublisher.publish(event);
    }

    public void startDelivery(DeliveryCreatedEvent event){
        Long orderId = event.orderId();

        Order order = orderSupport.getOrder(orderId);

        if (EnumSet.of(SHIPPING, DELIVERY_ERROR, SHIPPED, CONFIRMED).contains(order.getState()))
            return;

        order.validateAndUpdateState(PAID, SHIPPING);
    }

    public void updateDeliveryStatus(DeliveryStatusUpdatedEvent event){
        Long orderId = event.orderId();
        DeliveryStatusCode afterStatus = event.afterStatus();

        Order order = orderSupport.getOrder(orderId);

        if (order.getState() == PAID)
            throw new BusinessException(ORDER_STATE_BAD_REQUEST);

        if (order.getState() != SHIPPING && order.getState() != DELIVERY_ERROR)
            return;

        if (afterStatus.isCompleted())
            order.shipped(LocalDateTime.now());
        else if (afterStatus == EXCEPTION)
            order.updateState(DELIVERY_ERROR);
        else
            order.updateState(SHIPPING);
    }
}
