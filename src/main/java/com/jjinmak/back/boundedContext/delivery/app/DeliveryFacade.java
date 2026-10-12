package com.jjinmak.back.boundedContext.delivery.app;

import com.jjinmak.back.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryFacade {

    private final DeliverySyncMemberUseCase deliverySyncMemberUseCase;

    @Transactional
    public void syncMember(MemberDto dto){
        deliverySyncMemberUseCase.syncMember(dto);
    }
}
