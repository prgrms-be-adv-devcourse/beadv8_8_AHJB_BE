package com.jjinmak.member.exception;

import com.jjinmak.common.exception.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    CURRENT_PASSWORD_MISMATCH("AUTH001", "현재 비밀번호가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED),
    SAME_AS_CURRENT_PASSWORD("AUTH002", "새 비밀번호가 현재 비밀번호와 동일합니다.", HttpStatus.BAD_REQUEST),
    WITHDRAWN_EMAIL("AUTH003", "가입할 수 없는 이메일입니다.", HttpStatus.CONFLICT),

    LOGIN_FAILED("AUTH101", "이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("AUTH103", "로그인 시도 횟수를 초과했습니다.", HttpStatus.LOCKED),
    INVALID_REFRESH_TOKEN("AUTH104", "유효하지 않은 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED),
    EXPIRED_REFRESH_TOKEN("AUTH105", "만료된 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED),
    INVALID_ACCESS_TOKEN("AUTH106", "유효하지 않은 액세스 토큰입니다.", HttpStatus.UNAUTHORIZED),
    EXPIRED_ACCESS_TOKEN("AUTH107", "만료된 액세스 토큰입니다.", HttpStatus.UNAUTHORIZED),

    INVALID_OAUTH_CODE("AUTH108", "유효하지 않은 인가 코드입니다. 다시 로그인해 주세요.", HttpStatus.UNAUTHORIZED),
    OAUTH_PROVIDER_ERROR("AUTH109", "외부 인증 서버와 통신하지 못했습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.BAD_GATEWAY),
    INVALID_SIGNUP_TOKEN("AUTH110", "유효하지 않은 가입 요청입니다. 다시 로그인해 주세요.", HttpStatus.UNAUTHORIZED),
    EXPIRED_SIGNUP_TOKEN("AUTH111", "가입 가능 시간이 만료되었습니다. 다시 로그인해 주세요.", HttpStatus.UNAUTHORIZED),
    ALREADY_LINKED_SOCIAL_ACCOUNT("AUTH112", "이미 가입된 소셜 계정입니다.", HttpStatus.CONFLICT),


    ;
    private final String code;
    private final String message;
    private final HttpStatus status;
}
