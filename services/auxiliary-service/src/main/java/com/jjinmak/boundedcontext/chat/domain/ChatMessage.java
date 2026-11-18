package com.jjinmak.boundedcontext.chat.domain;

import com.jjinmak.boundedcontext.chat.in.dto.ChatMessageDto;
import com.jjinmak.boundedcontext.chat.in.dto.ChatMessagePushDto;
import com.jjinmak.boundedcontext.chat.in.dto.ChatPushType;
import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = {
        @Index(name = "idx_chat_message_room_id", columnList = "room_id, id")
})
public class ChatMessage extends BaseIdAndTime {

    @NotNull
    private Long roomId;

    /** 보낸 회원 id. 시스템 메시지는 null */
    private Long senderId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private ChatMessageType type;

    @NotNull
    @Column(length = 1000)
    private String content;

    private ChatMessage(Long roomId, Long senderId, ChatMessageType type, String content) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.type = type;
        this.content = content;
    }

    /** 회원이 보낸 메시지 */
    public static ChatMessage text(Long roomId, Long senderId, String content) {
        return new ChatMessage(roomId, senderId, ChatMessageType.TEXT, content);
    }

    /** 서버가 넣는 안내 메시지 (방 생성 등). 보낸 사람이 없어 누구에게도 "내 메시지"가 아니다 */
    public static ChatMessage system(Long roomId, String content) {
        return new ChatMessage(roomId, null, ChatMessageType.SYSTEM, content);
    }

    public boolean isSystem() {
        return type == ChatMessageType.SYSTEM;
    }

    public boolean isSentBy(Long memberId) {
        return senderId != null && senderId.equals(memberId);
    }

    public ChatMessageDto dto(Long memberId) {
        return new ChatMessageDto(
                getId(),
                isSentBy(memberId),
                content,
                getCreatedAt()
        );
    }

    public ChatMessagePushDto pushDto(ChatRole senderRole) {
        return new ChatMessagePushDto(
                ChatPushType.MESSAGE,
                roomId,
                getId(),
                senderRole,
                content,
                getCreatedAt()
        );
    }
}