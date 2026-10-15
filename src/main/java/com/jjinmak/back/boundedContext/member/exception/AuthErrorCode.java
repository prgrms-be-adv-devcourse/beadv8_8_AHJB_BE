package com.jjinmak.back.boundedContext.member.exception;

import com.jjinmak.back.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    CURRENT_PASSWORD_MISMATCH("AUTH001", "현재 비밀번호가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED),
    SAME_AS_CURRENT_PASSWORD("AUTH002", "새 비밀번호가 현재 비밀번호와 동일합니다.", HttpStatus.BAD_REQUEST),
    WITHDRAWN_EMAIL("AUTH003", "가입할 수 없는 이메일입니다.", HttpStatus.CONFLICT),
    MEMBER_NOT_FOUND("AUTH004", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    LOGIN_FAILED("AUTH101", "이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),
    ACCOUNT_BANNED("AUTH102", "차단된 계정입니다.", HttpStatus.FORBIDDEN),
    ACCOUNT_LOCKED("AUTH103", "로그인 시도 횟수를 초과했습니다.", HttpStatus.LOCKED),
    INVALID_REFRESH_TOKEN("AUTH104", "유효하지 않은 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED),
    EXPIRED_REFRESH_TOKEN("AUTH105", "만료된 토큰입니다.", HttpStatus.UNAUTHORIZED),
    INVALID_ACCESS_TOKEN("AUTH106", "유효하지 않은 액세스 토큰입니다.", HttpStatus.UNAUTHORIZED),
    EXPIRED_ACCESS_TOKEN("AUTH107", "만료된 액세스 토큰입니다.", HttpStatus.UNAUTHORIZED),

    SANCTION_NOT_FOUND("AUTH108", "제재 내역을 찾을 수 없습니다", HttpStatus.NOT_FOUND)

    ;
    private final String code;
    private final String message;
    private final HttpStatus status;
}