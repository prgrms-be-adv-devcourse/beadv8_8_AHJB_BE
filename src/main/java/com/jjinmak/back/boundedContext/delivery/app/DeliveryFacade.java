package com.jjinmak.back.boundedContext.delivery.app;

import com.jjinmak.back.shared.member.dto.MemberDto;
import com.jjinmak.back.shared.order.event.OrderSendProductEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryFacade {

    private final DeliveryUseCase deliveryUseCase;
    private final DeliverySyncMemberUseCase deliverySyncMemberUseCase;

    @Transactional
    public void syncMember(MemberDto dto){
        deliverySyncMemberUseCase.syncMember(dto);
    }

    @Transactional
    public void createDelivery(OrderSendProductEvent event){
        deliveryUseCase.createDelivery(event);
    }
}
