package com.jjinmak.delivery.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.delivery.domain.CarrierType;
import com.jjinmak.contracts.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.contracts.order.event.OrderSendProductEvent;
import com.jjinmak.delivery.app.dto.DeliveryResponseDto;
import com.jjinmak.delivery.app.event.WebhookRegisterEvent;
import com.jjinmak.delivery.domain.Delivery;
import com.jjinmak.delivery.domain.DeliveryMember;
import com.jjinmak.delivery.out.eventPublisher.EventPublisher;
import com.jjinmak.delivery.out.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.delivery.exception.DeliveryErrorCode.DELIVERY_BAD_REQUEST;

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

        WebhookRegisterEvent webhookRegisterEvent = new WebhookRegisterEvent(delivery.getId(), delivery.getOrderId(), carrier, waybill);

        eventPublisher.publish(webhookRegisterEvent);
    }

    public DeliveryResponseDto updateDeliveryData(Long sellerId, Long orderId, CarrierType carrier, String waybill){
        Delivery delivery = deliverySupport.getDeliveryWithParticipant(orderId);
        DeliveryMember seller = deliverySupport.getMember(sellerId);

        delivery.validateSeller(seller);

        if (delivery.getStatus().isCompleted()){
            throw new BusinessException(DELIVERY_BAD_REQUEST);
        }

        delivery.update(carrier, waybill);

        WebhookRegisterEvent webhookRegisterEvent = new WebhookRegisterEvent(delivery.getId(), delivery.getOrderId(), carrier, waybill);

        eventPublisher.publish(webhookRegisterEvent);

        return new DeliveryResponseDto(delivery.getOrderId(), delivery.getCarrier(), delivery.getWaybill());
    }
}
