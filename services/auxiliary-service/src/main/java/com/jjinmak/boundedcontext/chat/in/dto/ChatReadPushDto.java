package com.jjinmak.boundedcontext.chat.in.dto;

import com.jjinmak.boundedcontext.chat.domain.ChatRole;

/** 상대가 어디까지 읽었는지 알리는 push. 내 메시지 id ≤ lastReadMessageId 이면 "읽음" */
public record ChatReadPushDto(
        ChatPushType type,
        Long roomId,
        ChatRole readerRole,
        Long lastReadMessageId
) {
    public static ChatReadPushDto from(ChatReadResultDto result) {
        return new ChatReadPushDto(ChatPushType.READ, result.roomId(), result.readerRole(), result.lastReadMessageId());
    }
}