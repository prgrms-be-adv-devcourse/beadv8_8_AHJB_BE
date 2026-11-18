package com.jjinmak.boundedcontext.chat.domain;

public enum ChatMessageType {
    /** 회원이 보낸 메시지 */
    TEXT,
    /** 서버가 넣는 안내 메시지 (보낸 사람 없음). 예: 방 생성 */
    SYSTEM
}