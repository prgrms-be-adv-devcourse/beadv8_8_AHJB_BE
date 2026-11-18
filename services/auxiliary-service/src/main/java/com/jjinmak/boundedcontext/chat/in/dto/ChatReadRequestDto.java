package com.jjinmak.boundedcontext.chat.in.dto;

/** PATCH /api/v1/chat-rooms/{roomId}/read 요청. 화면에 보이는 마지막 메시지 id */
public record ChatReadRequestDto(
        Long lastReadMessageId
) {
}