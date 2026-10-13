package com.jjinmak.back.boundedContext.cart.exception;

import com.jjinmak.back.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CartErrorCode implements ErrorCode {

    CART_ITEM_NOT_FOUND("CART001", "존재하지 않는 상품입니다.", HttpStatus.NOT_FOUND),
    CART_ITEM_FORBIDDEN("CART002", "상품에 접근할 수 없습니다.", HttpStatus.FORBIDDEN)
    ;

    private final String code;
    private final String message;
    private final HttpStatus status;
}
