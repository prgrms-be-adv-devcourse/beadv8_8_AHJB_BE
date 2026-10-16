package com.jjinmak.back.boundedContext.cart.app;

import com.jjinmak.back.boundedContext.cart.domain.CartMember;
import com.jjinmak.back.boundedContext.cart.out.CartMemberRepository;
import com.jjinmak.back.shared.member.dto.MemberDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartSyncMemberUseCaseTest {

    @Mock
    CartMemberRepository cartMemberRepository;

    @InjectMocks
    CartSyncMemberUseCase cartSyncMemberUseCase;

    @Test
    @DisplayName("처음 받은 회원이면 새 CartMember를 저장한다")
    void syncMember_newMember() {
        UUID uuid = UUID.randomUUID();
        when(cartMemberRepository.findById(1L)).thenReturn(Optional.empty());

        cartSyncMemberUseCase.syncMember(new MemberDto(1L, uuid, "낙찰자"));

        ArgumentCaptor<CartMember> captor = ArgumentCaptor.forClass(CartMember.class);
        verify(cartMemberRepository).save(captor.capture());

        CartMember saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getUuid()).isEqualTo(uuid);
        assertThat(saved.getNickname()).isEqualTo("낙찰자");
    }

    @Test
    @DisplayName("이미 있는 회원이면 새로 만들지 않고 닉네임만 바꿔서 저장한다")
    void syncMember_existingMember() {
        UUID uuid = UUID.randomUUID();
        CartMember existing = new CartMember(1L, uuid, "수정 전");
        when(cartMemberRepository.findById(1L)).thenReturn(Optional.of(existing));

        cartSyncMemberUseCase.syncMember(new MemberDto(1L, uuid, "수정 후"));

        verify(cartMemberRepository).save(existing);
        assertThat(existing.getNickname()).isEqualTo("수정 후");
    }
}
