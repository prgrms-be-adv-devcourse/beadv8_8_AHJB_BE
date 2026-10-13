package com.jjinmak.back.boundedContext.delivery.app;

import com.jjinmak.back.boundedContext.delivery.app.dto.DeliveryResponseDto;
import com.jjinmak.back.boundedContext.delivery.app.dto.DeliveryStatusDto;
import com.jjinmak.back.shared.delivery.domain.CarrierType;
import com.jjinmak.back.boundedContext.delivery.app.event.WebhookRegisterEvent;
import com.jjinmak.back.shared.member.dto.MemberDto;
import com.jjinmak.back.shared.order.event.OrderSendProductEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
    public DeliveryResponseDto updateDeliveryData(UUID sellerId, Long orderId, CarrierType carrier, String waybill){
        return deliveryUseCase.updateDeliveryData(sellerId, orderId, carrier, waybill);
    }

    @Transactional(readOnly = true)
    public List<Long> getUnshippedDeliveryIds(){
        return deliverySchedulerUseCase.getUnshippedDeliveryIds();
    }

    public void updateWebhook(Long deliveryId){
        deliverySchedulerUseCase.updateWebhook(deliveryId);
    }

    public DeliveryStatusDto getDeliveryStatus(UUID memberId, Long orderId){
        return deliveryTrackerUseCase.trackLastStatus(memberId, orderId);
    }

    public void registerWebhook(WebhookRegisterEvent event){
        deliveryTrackerUseCase.registerWebhook(event);
    }
}
