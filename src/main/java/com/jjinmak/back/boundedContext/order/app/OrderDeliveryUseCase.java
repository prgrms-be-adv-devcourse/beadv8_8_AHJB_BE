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

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.*;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.*;

@Service
@RequiredArgsConstructor
public class OrderDeliveryUseCase {

    private final Set<DeliveryStatusCode> ORDER_SHIPPED = EnumSet.of(DELIVERED, AVAILABLE_FOR_PICKUP);

    private final OrderSupport orderSupport;
    private final EventPublisher eventPublisher;

    public void requestDelivery(UUID sellerId, Long orderId, CarrierType carrier, String waybill){
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

        if (!ORDER_SHIPPED.contains(afterStatus) || order.getState() != SHIPPING) {
            return;
        }

        order.shipped(event.updatedAt());
    }
}
