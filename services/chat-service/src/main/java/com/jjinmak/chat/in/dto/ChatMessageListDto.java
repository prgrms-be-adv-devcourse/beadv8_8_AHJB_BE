package com.jjinmak.chat.in.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record ChatMessageListDto(
        List<ChatMessageDto> content,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
    public static ChatMessageListDto from(Page<ChatMessageDto> page) {
        return new ChatMessageListDto(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.hasNext()
        );
    }
}