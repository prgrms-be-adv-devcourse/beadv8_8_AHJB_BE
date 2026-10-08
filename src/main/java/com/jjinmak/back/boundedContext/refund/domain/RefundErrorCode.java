package com.jjinmak.back.boundedContext.refund.domain;

import com.jjinmak.back.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RefundErrorCode implements ErrorCode {
    ORDER_NOT_FOUND("REFUND001", "존재하지 않는 주문", HttpStatus.NOT_FOUND),
    NOT_ORDER_WINNER("REFUND002", "본인 주문만 환불 요청 가능", HttpStatus.FORBIDDEN),
    NOT_REFUNDABLE("REFUND003", "구매확정된 주문은 환불 불가", HttpStatus.CONFLICT),
    ALREADY_REQUESTED("REFUND004", "이미 환불 요청된 주문", HttpStatus.CONFLICT),
    DETAIL_REQUIRED("REFUND005", "기타 사유는 상세 내용 필수", HttpStatus.BAD_REQUEST)
    ;

    private final String code;
    private final String message;
    private final HttpStatus status;
}
