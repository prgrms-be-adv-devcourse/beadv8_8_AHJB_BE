package com.jjinmak.chat.exception;

import com.jjinmak.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements ErrorCode {
    ROOM_NOT_FOUND("CHAT001", "존재하지 않는 채팅방", HttpStatus.NOT_FOUND),
    NOT_PARTICIPANT("CHAT002", "채팅방 참여자만 접근 가능", HttpStatus.FORBIDDEN),
    ROOM_CLOSED("CHAT003", "종료된 채팅방", HttpStatus.CONFLICT),
    PRODUCT_NOT_FOUND("CHAT004", "채팅 상품 복사본 없음", HttpStatus.NOT_FOUND),
    INVALID_MESSAGE("CHAT005", "메시지는 1~1000자", HttpStatus.BAD_REQUEST)
    ;

    private final String code;
    private final String message;
    private final HttpStatus status;
}