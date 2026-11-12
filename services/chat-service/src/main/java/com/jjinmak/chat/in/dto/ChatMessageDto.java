package com.jjinmak.chat.in.dto;

import java.time.LocalDateTime;

public record ChatMessageDto(
        Long messageId,
        boolean mine,
        String content,
        LocalDateTime createdAt
) {
}