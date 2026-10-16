package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.OrderMemberRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSyncMemberUseCaseTest {

    @Mock
    OrderMemberRepository orderMemberRepository;

    @InjectMocks
    OrderSyncMemberUseCase orderSyncMemberUseCase;

    @Test
    @DisplayName("처음 받은 회원이면 새 OrderMember를 저장하고, 저장된 회원을 반환한다")
    void syncMember_newMember() {
        UUID uuid = UUID.randomUUID();
        OrderMember stored = new OrderMember(1L, uuid, "낙찰자");
        when(orderMemberRepository.findById(1L)).thenReturn(Optional.empty());
        when(orderMemberRepository.save(any(OrderMember.class))).thenReturn(stored);

        OrderMember result = orderSyncMemberUseCase.syncMember(new MemberDto(1L, uuid, "낙찰자"));

        ArgumentCaptor<OrderMember> captor = ArgumentCaptor.forClass(OrderMember.class);
        verify(orderMemberRepository).save(captor.capture());

        OrderMember saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getUuid()).isEqualTo(uuid);
        assertThat(saved.getNickname()).isEqualTo("낙찰자");
        assertThat(result).isSameAs(stored);
    }

    @Test
    @DisplayName("이미 있는 회원이면 새로 만들지 않고 닉네임만 바꿔서 저장한다")
    void syncMember_existingMember() {
        UUID uuid = UUID.randomUUID();
        OrderMember existing = new OrderMember(1L, uuid, "수정 전");
        when(orderMemberRepository.findById(1L)).thenReturn(Optional.of(existing));

        orderSyncMemberUseCase.syncMember(new MemberDto(1L, uuid, "수정 후"));

        verify(orderMemberRepository).save(existing);
        assertThat(existing.getNickname()).isEqualTo("수정 후");
    }
}
