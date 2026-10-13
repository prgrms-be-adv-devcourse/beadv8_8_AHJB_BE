package com.jjinmak.back.boundedContext.delivery.app;

import com.jjinmak.back.boundedContext.delivery.app.dto.DeliveryStatusDto;
import com.jjinmak.back.boundedContext.delivery.domain.Delivery;
import com.jjinmak.back.boundedContext.delivery.domain.DeliveryMember;
import com.jjinmak.back.boundedContext.delivery.out.DeliveryRepository;
import com.jjinmak.back.boundedContext.delivery.out.DeliveryTracker;
import com.jjinmak.back.boundedContext.delivery.out.dto.DeliveryStatusResponseDto;
import com.jjinmak.back.shared.delivery.domain.CarrierType;
import com.jjinmak.back.boundedContext.delivery.app.event.WebhookRegisterEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryTrackerUseCase {

    private final DeliverySupport deliverySupport;
    private final DeliveryTracker deliveryTracker;
    private final DeliveryRepository deliveryRepository;

    public DeliveryStatusDto trackLastStatus(UUID memberId, Long orderId){
        Delivery delivery = deliverySupport.getDeliveryWithParticipant(orderId);
        DeliveryMember member = deliverySupport.getMember(memberId);

        delivery.validateParticipant(member);

        DeliveryStatusResponseDto.LastEvent lastEvent = deliveryTracker
                .trackLastStatus(delivery.getCarrier(), delivery.getWaybill())
                .data().track().lastEvent();

        deliveryRepository.updateStatus(delivery.getId(), delivery.getCarrier(), delivery.getWaybill(), lastEvent.status().code());

        return new DeliveryStatusDto(
                orderId,
                lastEvent.status().code(),
                lastEvent.status().code().getMessage(),
                lastEvent.time() == null ? null : lastEvent.time().toLocalDateTime()
        );
    }

    public void registerWebhook(WebhookRegisterEvent event){
        Long deliveryId = event.deliveryId();
        CarrierType carrier = event.carrier();
        String waybill = event.waybill();

        deliveryTracker.registerWebhook(deliveryId, carrier, waybill, 2);
    }
}
