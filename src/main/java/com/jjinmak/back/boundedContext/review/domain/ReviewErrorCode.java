package com.jjinmak.back.boundedContext.review.domain;

import com.jjinmak.back.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {

    REVIEWABLE_ORDER_NOT_FOUND("REVIEW001", "후기를 작성할 수 있는 주문이 아닙니다.", HttpStatus.NOT_FOUND),
    NOT_ORDER_BUYER("REVIEW002", "본인이 구매한 주문에만 후기를 작성할 수 있습니다.", HttpStatus.FORBIDDEN),
    ALREADY_REVIEWED("REVIEW003", "이미 후기를 작성한 주문입니다.", HttpStatus.BAD_REQUEST),
    REVIEW_PERIOD_EXPIRED("REVIEW004", "후기 작성 기간(구매확정 후 30일)이 지났습니다.", HttpStatus.BAD_REQUEST),
    INVALID_RATING("REVIEW005", "평점은 0.5점 단위로 0.5점부터 5.0점까지 입력할 수 있습니다.", HttpStatus.BAD_REQUEST),
    REVIEW_NOT_FOUND("REVIEW006", "존재하지 않는 후기입니다.", HttpStatus.NOT_FOUND),
    NOT_REVIEW_WRITER("REVIEW007", "본인이 작성한 후기만 수정/삭제할 수 있습니다.", HttpStatus.FORBIDDEN)
    ;
    private final String code;
    private final String message;
    private final HttpStatus status;
}
