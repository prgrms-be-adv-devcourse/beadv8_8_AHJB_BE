package com.jjinmak.boundedcontext.market.shared.app;

import com.jjinmak.boundedcontext.market.shared.domain.MarketMember;
import com.jjinmak.boundedcontext.market.shared.out.MarketMemberRepository;
import com.jjinmak.contracts.member.dto.MemberDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MarketMemberSyncUseCaseTest {

    @Mock MarketMemberRepository marketMemberRepository;

    @InjectMocks MarketMemberSyncUseCase useCase;

    private MemberDto 회원(String nickname) {
        return new MemberDto(7L, UUID.randomUUID(), nickname);
    }

    @Test
    void 처음_받은_회원이면_새로_저장한다() {
        given(marketMemberRepository.findById(7L)).willReturn(Optional.empty());
        given(marketMemberRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        MarketMember member = useCase.syncMember(회원("경매왕"));

        assertThat(member.getId()).isEqualTo(7L);
        assertThat(member.getNickname()).isEqualTo("경매왕");
    }

    @Test
    void 이미_있는_회원이면_닉네임만_갱신한다() {
        MarketMember existing = new MarketMember(7L, "옛닉네임");
        given(marketMemberRepository.findById(7L)).willReturn(Optional.of(existing));
        given(marketMemberRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        MarketMember member = useCase.syncMember(회원("새닉네임"));

        assertThat(member).isSameAs(existing);
        assertThat(member.getNickname()).isEqualTo("새닉네임");
    }
}
