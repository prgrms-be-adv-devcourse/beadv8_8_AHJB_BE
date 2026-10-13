package com.jjinmak.back.boundedContext.order.exception;

import com.jjinmak.back.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND("ORDER001", "존재하지 않는 주문입니다.", HttpStatus.NOT_FOUND),
    ORDER_FORBIDDEN("ORDER002", "주문에 대한 권한이 없습니다.", HttpStatus.FORBIDDEN),
    ORDER_STATE_BAD_REQUEST("ORDER003", "잘못된 상태 요청입니다.", HttpStatus.BAD_REQUEST),
    ORDER_GROUP_NOT_FOUND("ORDER004", "존재하지 않는 주문그룹입니다.", HttpStatus.NOT_FOUND),
    ORDER_BAD_REQUEST("ORDER005", "잘못된 주문 요청입니다.", HttpStatus.BAD_REQUEST)
    ;

    private final String code;
    private final String message;
    private final HttpStatus status;
}
