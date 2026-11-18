package com.jjinmak.boundedcontext.chat.in.dto;

/** /topic/chat-rooms/{roomId} 로 나가는 push 종류. 프론트는 이 값으로 분기한다 */
public enum ChatPushType {
    MESSAGE,
    READ
}