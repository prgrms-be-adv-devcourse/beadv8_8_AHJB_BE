package com.jjinmak.boundedcontext.chat.in.dto;

import com.jjinmak.boundedcontext.chat.domain.ChatRole;
import com.jjinmak.boundedcontext.chat.domain.ChatRoomStatus;

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
        Long counterpartLastReadId,
        LocalDateTime createdAt
) {
}