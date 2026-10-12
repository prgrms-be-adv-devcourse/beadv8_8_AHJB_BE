package com.jjinmak.back.boundedContext.delivery.app;

import com.jjinmak.back.boundedContext.delivery.domain.Delivery;
import com.jjinmak.back.boundedContext.delivery.domain.DeliveryMember;
import com.jjinmak.back.boundedContext.delivery.out.DeliveryMemberRepository;
import com.jjinmak.back.boundedContext.delivery.out.DeliveryRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.jjinmak.back.boundedContext.delivery.exception.DeliveryErrorCode.*;
import static com.jjinmak.back.global.exception.CommonErrorCode.*;

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

    public Delivery getDelivery(Long orderId){
        return deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(DELIVERY_NOT_FOUND));
    }

    public Delivery getDeliveryWithParticipant(Long orderId){
        return deliveryRepository.findByOrderIdWithWinnerAndSeller(orderId)
                .orElseThrow(() -> new BusinessException(DELIVERY_NOT_FOUND));
    }
}
