package com.jjinmak.delivery.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.delivery.domain.Delivery;
import com.jjinmak.delivery.domain.DeliveryMember;
import com.jjinmak.delivery.out.repository.DeliveryMemberRepository;
import com.jjinmak.delivery.out.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.jjinmak.common.exception.CommonErrorCode.USER_NOT_FOUND;
import static com.jjinmak.delivery.exception.DeliveryErrorCode.DELIVERY_NOT_FOUND;

@Component
@RequiredArgsConstructor
public class DeliverySupport {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryMemberRepository deliveryMemberRepository;

    public DeliveryMember getMember(Long id){
        return deliveryMemberRepository.findById(id)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
    }

    public DeliveryMember getMember(UUID uuid){
        return deliveryMemberRepository.findByUuid(uuid)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
    }

    public Delivery getDeliveryById(Long deliveryId){
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(DELIVERY_NOT_FOUND));
    }

    public Delivery getDeliveryByOrderId(Long orderId){
        return deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(DELIVERY_NOT_FOUND));
    }

    public Delivery getDeliveryWithParticipant(Long orderId){
        return deliveryRepository.findByOrderIdWithWinnerAndSeller(orderId)
                .orElseThrow(() -> new BusinessException(DELIVERY_NOT_FOUND));
    }
}
