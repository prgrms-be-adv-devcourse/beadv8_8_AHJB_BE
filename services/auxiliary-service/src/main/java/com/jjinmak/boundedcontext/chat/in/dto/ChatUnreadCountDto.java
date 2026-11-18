package com.jjinmak.boundedcontext.chat.in.dto;

/** GET /api/v1/chat-rooms/unread-count 응답. 안 읽은 메시지가 있는 방의 수 */
public record ChatUnreadCountDto(
        long count
) {
}