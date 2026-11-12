package com.jjinmak.chat.domain;

import com.jjinmak.chat.in.dto.ChatMessageDto;
import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = {
        @Index(name = "idx_chat_message_room_id", columnList = "room_id, id")
})
public class ChatMessage extends BaseIdAndTime {

    @NotNull
    private Long roomId;

    @NotNull
    private Long senderId;

    @NotNull
    @Column(length = 1000)
    private String content;

    public ChatMessage(Long roomId, Long senderId, String content) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.content = content;
    }

    public ChatMessageDto dto(Long memberId) {
        return new ChatMessageDto(
                getId(),
                senderId.equals(memberId),
                content,
                getCreatedAt()
        );
    }
}