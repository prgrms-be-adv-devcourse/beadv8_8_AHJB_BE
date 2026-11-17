package com.jjinmak.boundedContext.delivery.app;

import com.jjinmak.contracts.member.dto.MemberDto;
import com.jjinmak.boundedContext.delivery.domain.DeliveryMember;
import com.jjinmak.boundedContext.delivery.out.repository.DeliveryMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliverySyncMemberUseCase {

    private final DeliveryMemberRepository deliveryMemberRepository;

    public void syncMember(MemberDto dto){
        Long id = dto.id();
        UUID uuid = dto.uuid();
        String nickname = dto.nickname();

        DeliveryMember member = deliveryMemberRepository.findById(id)
                        .orElseGet(() -> new DeliveryMember(id, uuid, nickname));

        member.sync(nickname);

        deliveryMemberRepository.save(member);
    }
}
