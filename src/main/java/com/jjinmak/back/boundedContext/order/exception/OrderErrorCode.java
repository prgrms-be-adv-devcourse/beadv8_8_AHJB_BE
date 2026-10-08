package com.jjinmak.back.boundedContext.order.exception;

import com.jjinmak.back.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND("ORDER001", "존재하지 않는 주문입니다.", HttpStatus.NOT_FOUND),
    ORDER_FORBIDDEN("ORDER002", "주문의 조회 권한이 없습니다.", HttpStatus.FORBIDDEN)
    ;

    private final String code;
    private final String message;
    private final HttpStatus status;
}
