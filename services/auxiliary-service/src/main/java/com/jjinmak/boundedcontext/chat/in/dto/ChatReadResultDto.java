package com.jjinmak.boundedcontext.chat.in.dto;

import com.jjinmak.boundedcontext.chat.domain.ChatRole;

/** 읽음 처리 결과. changed가 false면 이미 그 위치 이상을 읽은 상태라 push하지 않는다 */
public record ChatReadResultDto(
        Long roomId,
        ChatRole readerRole,
        Long lastReadMessageId,
        boolean changed
) {
}