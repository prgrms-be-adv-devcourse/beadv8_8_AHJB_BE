package com.jjinmak.back.boundedContext.delivery.app;

import com.jjinmak.back.boundedContext.delivery.domain.Delivery;
import com.jjinmak.back.boundedContext.delivery.domain.DeliveryMember;
import com.jjinmak.back.boundedContext.delivery.out.DeliveryRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.shared.delivery.domain.CarrierType;
import com.jjinmak.back.shared.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.back.shared.order.event.OrderSendProductEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryUseCase {

    private final DeliverySupport deliverySupport;
    private final DeliveryRepository deliveryRepository;
    private final EventPublisher eventPublisher;

    public void createDelivery(OrderSendProductEvent event){
        Long orderId = event.orderId();

        DeliveryMember winner = deliverySupport.getMember(event.winnerId());
        DeliveryMember seller = deliverySupport.getMember(event.sellerId());

        CarrierType carrier = event.carrier();
        String waybill = event.waybill();


        Delivery delivery = new Delivery(orderId, winner, seller, carrier, waybill);

        deliveryRepository.save(delivery);

        DeliveryCreatedEvent deliveryCreatedEvent = new DeliveryCreatedEvent(orderId);

        eventPublisher.publish(deliveryCreatedEvent);
    }
}
