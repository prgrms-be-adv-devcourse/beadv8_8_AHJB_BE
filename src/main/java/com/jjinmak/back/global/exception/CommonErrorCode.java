package com.jjinmak.back.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode{

    BAD_REQUEST("COMMON001", "잘못된 요청(필수값 누락, 형식 오류)", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("COMMON002", "인증 필요, 토큰 만료", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("COMMON003", "접근 권한 없음", HttpStatus.FORBIDDEN),
    INTERNAL_ERROR("COMMON004", "서버 내부 오류", HttpStatus.INTERNAL_SERVER_ERROR),

    SUSPENDED_ACCOUNT("COMMON101", "정지 상태로 이용 불가", HttpStatus.FORBIDDEN),
    BANNED_ACCOUNT("COMMON102", "차단 상태로 이용 불가", HttpStatus.FORBIDDEN),
    USER_NOT_FOUND("COMMON103", "존재하지 않는 회원", HttpStatus.NOT_FOUND)
    ;
    private final String code;
    private final String message;
    private final HttpStatus status;
}
