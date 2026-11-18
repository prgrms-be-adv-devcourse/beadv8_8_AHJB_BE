package com.jjinmak.boundedcontext.chat.in.dto;

import com.jjinmak.boundedcontext.chat.domain.ChatMessageType;

import java.time.LocalDateTime;

public record ChatMessageDto(
        Long messageId,
        ChatMessageType type,
        boolean mine,
        String content,
        LocalDateTime createdAt
) {
}