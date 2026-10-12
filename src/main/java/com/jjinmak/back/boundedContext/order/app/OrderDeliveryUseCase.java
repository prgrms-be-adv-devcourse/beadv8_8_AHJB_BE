package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.shared.delivery.domain.CarrierType;
import com.jjinmak.back.shared.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.back.shared.order.event.OrderSendProductEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.*;

@Service
@RequiredArgsConstructor
public class OrderDeliveryUseCase {

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
}
