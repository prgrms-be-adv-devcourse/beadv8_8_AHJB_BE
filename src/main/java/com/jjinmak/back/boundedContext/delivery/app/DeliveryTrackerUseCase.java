package com.jjinmak.back.boundedContext.delivery.app;

import com.jjinmak.back.boundedContext.delivery.app.dto.DeliveryStatusDto;
import com.jjinmak.back.boundedContext.delivery.app.event.CallbackRequestedEvent;
import com.jjinmak.back.boundedContext.delivery.domain.Delivery;
import com.jjinmak.back.boundedContext.delivery.domain.DeliveryMember;
import com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode;
import com.jjinmak.back.boundedContext.delivery.out.DeliveryRepository;
import com.jjinmak.back.boundedContext.delivery.out.DeliveryTracker;
import com.jjinmak.back.boundedContext.delivery.out.dto.DeliveryStatusResponseDto;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.shared.delivery.domain.CarrierType;
import com.jjinmak.back.boundedContext.delivery.app.event.WebhookRegisterEvent;
import com.jjinmak.back.shared.delivery.event.DeliveryStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryTrackerUseCase {

    private final TransactionTemplate transactionTemplate;
    private final EventPublisher eventPublisher;

    private final DeliverySupport deliverySupport;
    private final DeliveryTracker deliveryTracker;
    private final DeliveryRepository deliveryRepository;

    public DeliveryStatusDto trackLastStatus(Long memberId, Long orderId){
        Delivery delivery = deliverySupport.getDeliveryWithParticipant(orderId);
        DeliveryMember member = deliverySupport.getMember(memberId);

        delivery.validateParticipant(member);

        DeliveryStatusResponseDto.LastEvent lastEvent = track(delivery);

        applyStatus(delivery, lastEvent);

        return new DeliveryStatusDto(
                orderId,
                lastEvent.status().code(),
                lastEvent.status().code().getMessage(),
                lastEvent.time() == null ? null : lastEvent.time().toLocalDateTime()
        );
    }

    public void trackLastStatus(Long deliveryId){
        Delivery delivery = deliverySupport.getDeliveryById(deliveryId);

        DeliveryStatusResponseDto.LastEvent lastEvent = track(delivery);

        applyStatus(delivery, lastEvent);
    }

    private DeliveryStatusResponseDto.LastEvent track(Delivery delivery){
        return deliveryTracker
                .trackLastStatus(delivery.getCarrier(), delivery.getWaybill())
                .data().track().lastEvent();
    }

    public void registerWebhook(WebhookRegisterEvent event){
        Long deliveryId = event.deliveryId();
        Long orderId = event.orderId();
        CarrierType carrier = event.carrier();
        String waybill = event.waybill();

        deliveryTracker.registerWebhook(deliveryId, orderId, carrier, waybill, 2);
    }

    public void webhookCallback(Long deliveryId, Long orderId, CarrierType carrier, String waybill){
        Delivery delivery = deliverySupport.getDeliveryById(deliveryId);

        if (!delivery.getOrderId().equals(orderId) || !delivery.getCarrier().equals(carrier) || !delivery.getWaybill().equals(waybill)){
            return;
        }

        eventPublisher.publish(new CallbackRequestedEvent(deliveryId));
    }

    private void applyStatus(Delivery delivery, DeliveryStatusResponseDto.LastEvent lastEvent){
        DeliveryStatusCode beforeCode = delivery.getStatus();
        DeliveryStatusCode afterCode = lastEvent.status().code();

        if (beforeCode.equals(afterCode))
            return;

        LocalDateTime updatedAt = lastEvent.time() == null ? LocalDateTime.now() : lastEvent.time().toLocalDateTime();

        transactionTemplate.executeWithoutResult(status -> {
            int updated = deliveryRepository.updateStatus(delivery.getId(), delivery.getCarrier(), delivery.getWaybill(), beforeCode, afterCode);

            if (updated == 1) {
                eventPublisher.publish(new DeliveryStatusUpdatedEvent(delivery.getOrderId(), beforeCode, afterCode, updatedAt));
            }
        });
    }
}
