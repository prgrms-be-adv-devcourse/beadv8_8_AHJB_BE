package com.jjinmak.boundedContext.chat.in.dto;

import com.jjinmak.boundedContext.chat.domain.ChatRole;
import com.jjinmak.boundedContext.chat.domain.ChatRoomStatus;

import java.time.LocalDateTime;

public record ChatRoomDetailDto(
        Long roomId,
        Long auctionId,
        Long productId,
        String productName,
        String productThumbnailUrl,
        String counterpartNickname,
        ChatRole myRole,
        ChatRoomStatus status,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt
) {
}