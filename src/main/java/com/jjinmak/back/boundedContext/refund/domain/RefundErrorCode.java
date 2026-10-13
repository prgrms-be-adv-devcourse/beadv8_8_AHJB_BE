package com.jjinmak.back.boundedContext.refund.domain;

import com.jjinmak.back.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RefundErrorCode implements ErrorCode {
    REFUND_NOT_FOUND("REFUND001", "존재하지 않는 환불", HttpStatus.NOT_FOUND),
    NOT_REFUND_PARTICIPANT("REFUND002", "환불 당사자만 조회 가능", HttpStatus.FORBIDDEN),
    ALREADY_REQUESTED("REFUND003", "이미 환불 요청된 주문", HttpStatus.CONFLICT)
    ;


    private final String code;
    private final String message;
    private final HttpStatus status;
}
