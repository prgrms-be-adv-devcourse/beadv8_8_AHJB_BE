package com.jjinmak.back.boundedContext.product.domain;

import com.jjinmak.back.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    // TODO: PRODUCT002(판매자 미등록), PRODUCT003(정지/차단)은 회원 컨텍스트 구현 후 추가
    PRODUCT_NOT_FOUND("PRODUCT001", "존재하지 않는 상품입니다.", HttpStatus.NOT_FOUND),
    POLICY_NOT_AGREED("PRODUCT004", "판매 정책에 동의하지 않았거나 정책 버전이 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    INVALID_INSTANT_WIN_PRICE("PRODUCT005", "즉시낙찰가는 시작가보다 커야 합니다.", HttpStatus.BAD_REQUEST),
    DIRECT_INPUT_REQUIRED("PRODUCT006", "직접 입력 값이 누락되었습니다.", HttpStatus.BAD_REQUEST)
    ;
    private final String code;
    private final String message;
    private final HttpStatus status;
}
