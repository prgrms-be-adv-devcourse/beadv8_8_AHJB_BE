package com.jjinmak.boundedcontext.market.shared.app;

import com.jjinmak.boundedcontext.market.shared.domain.MarketMember;
import com.jjinmak.boundedcontext.market.shared.out.MarketMemberRepository;
import com.jjinmak.contracts.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketMemberSyncUseCase {

    private final MarketMemberRepository marketMemberRepository;

    // 회원을 복제한다. 같은 회원이 다시 오면 닉네임만 갱신한다. (같은 이벤트를 두 번 받아도 결과가 같다)
    @Transactional
    public MarketMember syncMember(MemberDto memberDto) {
        Long id = memberDto.id();
        String nickname = memberDto.nickname();

        MarketMember member = marketMemberRepository.findById(id)
                .orElseGet(() -> new MarketMember(id, nickname));
        member.sync(nickname);

        return marketMemberRepository.save(member);
    }
}
