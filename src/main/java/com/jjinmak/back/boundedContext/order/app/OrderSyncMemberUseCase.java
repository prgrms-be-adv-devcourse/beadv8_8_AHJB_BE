package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.OrderMemberRepository;
import com.jjinmak.back.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderSyncMemberUseCase {

    private final OrderMemberRepository orderMemberRepository;

    /**
     * 멤버 생성 이벤트를 수신받아, OrderMember를 생성한다.
     * @param memberDto - 멤버UUID, 닉네임
     * @return OrderMember - 생성된 Member
     */
    public OrderMember syncMember(MemberDto memberDto){
        UUID uuid = memberDto.uuid();
        String nickname = memberDto.nickname();

        OrderMember member = orderMemberRepository.findByUuid(uuid)
                .orElseGet(() -> new OrderMember(uuid, nickname));

        member.sync(nickname);

        return orderMemberRepository.save(member);
    }
}
