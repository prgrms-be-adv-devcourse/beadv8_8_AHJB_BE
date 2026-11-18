package com.jjinmak.boundedcontext.chat.in.dto;

import com.jjinmak.boundedcontext.chat.domain.ChatRole;
import com.jjinmak.boundedcontext.chat.domain.ChatRoomStatus;

import java.time.LocalDateTime;

public record ChatRoomItemDto(
        Long roomId,
        String productName,
        String productThumbnailUrl,
        String counterpartNickname,
        ChatRole myRole,
        ChatRoomStatus status,
        String lastMessageContent,
        LocalDateTime lastMessageAt,
        long unreadCount
) {
}