package com.jjinmak.boundedcontext.market.shared.in;

import com.jjinmak.boundedcontext.market.shared.app.MarketMemberSyncUseCase;
import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.member.dto.MemberDto;
import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MarketMemberEventListenerTest {

    @Mock MarketMemberSyncUseCase marketMemberSyncUseCase;

    @InjectMocks MarketMemberEventListener listener;

    private MemberDto 회원(Long id, String nickname) {
        return new MemberDto(id, UUID.randomUUID(), nickname);
    }

    private void DLT로_보낸다(MemberCreatedEvent event) {
        assertThatThrownBy(() -> listener.handle(event))
                .isInstanceOf(InvalidEventException.class);
        then(marketMemberSyncUseCase).shouldHaveNoInteractions();
    }

    @Test
    void 받은_회원을_market_회원으로_복제한다() {
        MemberDto member = 회원(7L, "경매왕");

        listener.handle(new MemberCreatedEvent(member));

        then(marketMemberSyncUseCase).should().syncMember(member);
    }

    @Test
    void 회원_정보가_없으면_DLT로_보낸다() {
        DLT로_보낸다(new MemberCreatedEvent(null));
    }

    @Test
    void 회원_id가_없으면_DLT로_보낸다() {
        DLT로_보낸다(new MemberCreatedEvent(회원(null, "경매왕")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void 닉네임이_없으면_DLT로_보낸다(String nickname) {
        DLT로_보낸다(new MemberCreatedEvent(회원(7L, nickname)));
    }
}
