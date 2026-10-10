package com.jjinmak.back.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    EXPIRED_TOKEN("AUTH105", "만료된 토큰 입니다.", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN("AUTH106", "유효하지 않은 엑세스 토큰", HttpStatus.UNAUTHORIZED)

    ;


    private final String code;
    private final String message;
    private final HttpStatus status;
}
