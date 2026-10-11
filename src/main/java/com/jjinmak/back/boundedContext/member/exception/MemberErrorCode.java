package com.jjinmak.back.boundedContext.member.exception;

import com.jjinmak.back.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    DUPLICATE_EMAIL("MEMBER001", "이미 사용중인 이메일입니다.", HttpStatus.CONFLICT),
    DUPLICATE_NICKNAME("MEMBER002", "이미 사용중인 닉네임입니다.", HttpStatus.CONFLICT),
    ALREADY_REGISTERED_SELLER("MEMBER003", "이미 판매자로 등록되어있습니다", HttpStatus.CONFLICT),
    EXIST_PROGRESSING_TRADE("MEMBER004", "진행중인 거래가 존재합니다.", HttpStatus.CONFLICT),
    DEPOSIT_BALANCE_EXISTS("MEMBER005", "예치금 잔액이 존재합니다.", HttpStatus.CONFLICT)
    ;



    private final String code;
    private final String message;
    private final HttpStatus status;
}
