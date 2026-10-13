package com.jjinmak.back.boundedContext.delivery.app;

import com.jjinmak.back.boundedContext.delivery.domain.Delivery;
import com.jjinmak.back.boundedContext.delivery.domain.DeliveryStatusCode;
import com.jjinmak.back.boundedContext.delivery.out.DeliveryRepository;
import com.jjinmak.back.boundedContext.delivery.out.DeliveryTracker;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.jjinmak.back.boundedContext.delivery.domain.DeliveryStatusCode.*;

@Service
@RequiredArgsConstructor
public class DeliverySchedulerUseCase {

    private final DeliverySupport deliverySupport;
    private final DeliveryTracker deliveryTracker;
    private final DeliveryRepository deliveryRepository;
    private final Set<DeliveryStatusCode> unshipped = EnumSet.of(
            UNKNOWN, WAITING, INFORMATION_RECEIVED, AT_PICKUP, IN_TRANSIT, OUT_FOR_DELIVERY, ATTEMPT_FAIL, EXCEPTION
    );

    public List<Long> getUnshippedDeliveryIds(){
        return deliveryRepository.findIdsByDeliveryStatusIn(unshipped);
    }

    public void updateWebhook(Long deliveryId){
        Delivery delivery = deliverySupport.getDeliveryById(deliveryId);

        deliveryTracker.registerWebhook(deliveryId, delivery.getCarrier(), delivery.getWaybill(), 2);
    }
}
