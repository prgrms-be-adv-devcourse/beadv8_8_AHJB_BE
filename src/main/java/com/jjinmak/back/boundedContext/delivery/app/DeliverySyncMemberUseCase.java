package com.jjinmak.back.boundedContext.delivery.app;

import com.jjinmak.back.boundedContext.delivery.domain.DeliveryMember;
import com.jjinmak.back.boundedContext.delivery.out.repository.DeliveryMemberRepository;
import com.jjinmak.back.shared.member.dto.MemberDto;
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
