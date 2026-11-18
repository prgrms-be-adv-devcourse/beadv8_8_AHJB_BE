package com.jjinmak.boundedcontext.chat.out;

/** 방별 안 읽은 메시지 수 (JPQL 생성자 표현식용) */
public record ChatUnreadCount(
        Long roomId,
        long count
) {
}