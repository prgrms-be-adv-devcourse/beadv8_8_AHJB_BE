package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.shared.delivery.domain.CarrierType;
import com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode;
import com.jjinmak.back.shared.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.back.shared.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.back.shared.order.event.OrderSendProductEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.*;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.*;

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

        order.validateAndUpdateState(PAID, SHIPPING);
    }

    public void updateDeliveryStatus(DeliveryStatusUpdatedEvent event){
        Long orderId = event.orderId();
        DeliveryStatusCode afterStatus = event.afterStatus();

        Order order = orderSupport.getOrder(orderId);

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
