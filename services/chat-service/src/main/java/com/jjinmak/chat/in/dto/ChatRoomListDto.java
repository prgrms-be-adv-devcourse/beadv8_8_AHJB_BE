package com.jjinmak.chat.in.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record ChatRoomListDto(
        List<ChatRoomItemDto> content,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
    public static ChatRoomListDto from(Page<ChatRoomItemDto> page) {
        return new ChatRoomListDto(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.hasNext()
        );
    }
}