package com.jjinmak.chat.domain;

import com.jjinmak.common.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 회원 도메인의 복제본. member.created 이벤트로 받은 닉네임을 채팅방·메시지 응답에 쓰기 위해 보관한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMember extends BaseManualIdAndTime {

    @NotNull
    @Column(unique = true)
    private UUID uuid;

    @NotNull
    @Column(length = 50)
    private String nickname;

    /**
     * @param memberId 회원 도메인의 원본 id (이 엔티티의 PK로 그대로 쓴다)
     */
    public ChatMember(Long memberId, UUID uuid, String nickname) {
        super(memberId);
        this.uuid = uuid;
        this.nickname = nickname;
    }

    public void sync(String nickname) {
        this.nickname = nickname;
    }
}