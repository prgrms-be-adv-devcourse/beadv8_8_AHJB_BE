package com.jjinmak.back.boundedContext.cart.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CartMemberTest {

    @Test
    @DisplayName("닉네임을 동기화하면 닉네임만 바뀐다")
    void sync_changesNicknameOnly() {
        UUID uuid = UUID.randomUUID();
        CartMember member = new CartMember(1L, uuid, "수정 전");

        member.sync("수정 후");

        assertThat(member.getNickname()).isEqualTo("수정 후");
        assertThat(member.getId()).isEqualTo(1L);
        assertThat(member.getUuid()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("아직 저장하지 않은 회원은 새 엔티티로 판단한다")
    void isNew_beforeSave() {
        CartMember member = new CartMember(1L, UUID.randomUUID(), "낙찰자");

        assertThat(member.isNew()).isTrue();
    }
}
