package com.jjinmak.back.boundedContext.delivery.app;

import com.jjinmak.back.boundedContext.delivery.app.dto.WebhookTargetDto;
import com.jjinmak.back.boundedContext.delivery.domain.Delivery;
import com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode;
import com.jjinmak.back.boundedContext.delivery.out.repository.DeliveryRepository;
import com.jjinmak.back.boundedContext.delivery.out.deliveryTracker.DeliveryTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.*;

@Service
@RequiredArgsConstructor
public class DeliverySchedulerUseCase {

    private final DeliveryTracker deliveryTracker;
    private final DeliveryRepository deliveryRepository;
    private final Set<DeliveryStatusCode> unshipped = EnumSet.of(
            UNKNOWN, WAITING, INFORMATION_RECEIVED, AT_PICKUP, IN_TRANSIT, OUT_FOR_DELIVERY, ATTEMPT_FAIL, EXCEPTION, WRONG
    );

    public List<WebhookTargetDto> getUnshippedDelivery(){
        List<Delivery> deliveries =  deliveryRepository.findAllByDeliveryStatusIn(unshipped);

        return deliveries.stream()
                .map((d) -> new WebhookTargetDto(d.getId(), d.getOrderId(), d.getCarrier(), d.getWaybill()))
                .toList();
    }

    public void updateWebhook(WebhookTargetDto delivery, DeliveryStatusCode latestStatus){
        if (latestStatus != null && latestStatus.isCompleted()){
            return;
        }
        deliveryTracker.registerWebhook(delivery.deliveryId(), delivery.orderId(), delivery.carrier(), delivery.waybill(), 2);
    }
}
