package com.jjinmak.back.boundedContext.delivery.app;

import com.jjinmak.back.boundedContext.delivery.domain.DeliveryMember;
import com.jjinmak.back.boundedContext.delivery.out.DeliveryMemberRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.jjinmak.back.global.exception.CommonErrorCode.*;

@Component
@RequiredArgsConstructor
public class DeliverySupport {

    private final DeliveryMemberRepository deliveryMemberRepository;

    public DeliveryMember getMember(Long id){
        return deliveryMemberRepository.findById(id)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
    }
}
