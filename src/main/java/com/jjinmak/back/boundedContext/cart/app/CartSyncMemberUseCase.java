package com.jjinmak.back.boundedContext.cart.app;

import com.jjinmak.back.boundedContext.cart.domain.CartMember;
import com.jjinmak.back.boundedContext.cart.out.CartMemberRepository;
import com.jjinmak.back.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartSyncMemberUseCase {

    private final CartMemberRepository cartMemberRepository;

    /**
     * 멤버 생성 이벤트를 수신받아, CartMember를 생성한다.
     * @param memberDto - 멤버ID, 멤버UUID, 닉네임
     * @return CartMember - 생성된 Member
     */
    public CartMember syncMember(MemberDto memberDto){
        Long id = memberDto.id();
        UUID uuid = memberDto.uuid();
        String nickname = memberDto.nickname();

        CartMember member = cartMemberRepository.findById(id)
                        .orElseGet(() -> new CartMember(id, uuid, nickname));

        member.sync(nickname);

        return cartMemberRepository.save(member);
    }
}
