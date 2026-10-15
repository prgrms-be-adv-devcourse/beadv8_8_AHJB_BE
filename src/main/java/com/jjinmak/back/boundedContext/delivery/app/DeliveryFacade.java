package com.jjinmak.back.boundedContext.delivery.app;

import com.jjinmak.back.boundedContext.delivery.app.dto.DeliveryResponseDto;
import com.jjinmak.back.boundedContext.delivery.app.dto.DeliveryStatusDto;
import com.jjinmak.back.boundedContext.delivery.app.dto.WebhookTargetDto;
import com.jjinmak.back.shared.delivery.domain.CarrierType;
import com.jjinmak.back.boundedContext.delivery.app.event.WebhookRegisterEvent;
import com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode;
import com.jjinmak.back.shared.member.dto.MemberDto;
import com.jjinmak.back.shared.order.event.OrderSendProductEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryFacade {

    private final DeliveryUseCase deliveryUseCase;
    private final DeliverySyncMemberUseCase deliverySyncMemberUseCase;
    private final DeliveryTrackerUseCase deliveryTrackerUseCase;
    private final DeliverySchedulerUseCase deliverySchedulerUseCase;

    @Transactional
    public void syncMember(MemberDto dto){
        deliverySyncMemberUseCase.syncMember(dto);
    }

    @Transactional
    public void createDelivery(OrderSendProductEvent event){
        deliveryUseCase.createDelivery(event);
    }

    @Transactional
    public DeliveryResponseDto updateDeliveryData(Long sellerId, Long orderId, CarrierType carrier, String waybill){
        return deliveryUseCase.updateDeliveryData(sellerId, orderId, carrier, waybill);
    }

    @Transactional(readOnly = true)
    public List<WebhookTargetDto> getUnshippedDelivery(){
        return deliverySchedulerUseCase.getUnshippedDelivery();
    }

    public void updateWebhook(WebhookTargetDto delivery, DeliveryStatusCode latestStatus){
        deliverySchedulerUseCase.updateWebhook(delivery, latestStatus);
    }

    public DeliveryStatusDto getDeliveryStatus(Long memberId, Long orderId){
        return deliveryTrackerUseCase.trackLastStatus(memberId, orderId);
    }

    public DeliveryStatusCode getDeliveryStatus(Long deliveryId){
        return deliveryTrackerUseCase.trackLastStatus(deliveryId);
    }

    public void registerWebhook(WebhookRegisterEvent event){
        deliveryTrackerUseCase.registerWebhook(event);
    }

    public void webhookCallback(Long deliveryId, Long orderId, CarrierType carrier, String waybill){
        deliveryTrackerUseCase.webhookCallback(deliveryId, orderId, carrier, waybill);
    }
}
