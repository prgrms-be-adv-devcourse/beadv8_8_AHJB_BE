package com.jjinmak.boundedContext.chat.in.dto;

import com.jjinmak.boundedContext.chat.domain.ChatRole;

import java.time.LocalDateTime;

public record ChatMessagePushDto(
        Long roomId,
        Long messageId,
        ChatRole senderRole,
        String content,
        LocalDateTime createdAt
) {
}