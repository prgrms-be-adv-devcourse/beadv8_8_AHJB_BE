package com.jjinmak.boundedcontext.chat.in.dto;

/** PATCH /api/v1/chat-rooms/{roomId}/read 응답 */
public record ChatReadResponseDto(
        Long roomId,
        Long lastReadMessageId
) {
}