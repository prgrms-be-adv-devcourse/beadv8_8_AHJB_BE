package com.jjinmak.chat.in.dto;

import com.jjinmak.chat.domain.ChatRole;
import com.jjinmak.chat.domain.ChatRoomStatus;

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