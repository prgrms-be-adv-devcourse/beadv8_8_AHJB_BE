package com.jjinmak.back.boundedContext.delivery.exception;

import com.jjinmak.back.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DeliveryErrorCode implements ErrorCode {

    DELIVERY_BAD_REQUEST("DELIVERY_001", "잘못된 배송 요청입니다.", HttpStatus.BAD_REQUEST),
    DELIVERY_NOT_FOUND("DELIVERY_002", "배송을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    DELIVERY_FORBIDDEN("DELIVERY_003", "배송 접근 권한이 없습니다.", HttpStatus.FORBIDDEN)
    ;

    private final String code;
    private final String message;
    private final HttpStatus status;
}
