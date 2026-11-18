package com.jjinmak.boundedcontext.chat.in.dto;

import com.jjinmak.boundedcontext.chat.domain.ChatRole;

import java.time.LocalDateTime;

public record ChatMessagePushDto(
        ChatPushType type,
        Long roomId,
        Long messageId,
        ChatRole senderRole,
        String content,
        LocalDateTime createdAt
) {
}