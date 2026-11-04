package com.jjinmak.order.app;

import com.jjinmak.contracts.member.dto.MemberDto;
import com.jjinmak.order.domain.OrderMember;
import com.jjinmak.order.out.repository.OrderMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderSyncMemberUseCase {

    private final OrderMemberRepository orderMemberRepository;

    /**
     * 멤버 생성 이벤트를 수신받아, OrderMember를 생성한다.
     * @param memberDto - 멤버ID, 멤버UUID, 닉네임
     * @return OrderMember - 생성된 Member
     */
    public OrderMember syncMember(MemberDto memberDto){
        Long id = memberDto.id();
        UUID uuid = memberDto.uuid();
        String nickname = memberDto.nickname();

        OrderMember member = orderMemberRepository.findById(id)
                .orElseGet(() -> new OrderMember(id, uuid, nickname));

        member.sync(nickname);

        return orderMemberRepository.save(member);
    }
}
