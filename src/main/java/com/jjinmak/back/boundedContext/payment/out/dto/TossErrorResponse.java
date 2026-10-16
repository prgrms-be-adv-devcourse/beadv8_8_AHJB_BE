package com.jjinmak.back.boundedContext.payment.out.dto;

/** 토스 에러 응답 본문. 예: { "code": "ALREADY_PROCESSED_PAYMENT", "message": "이미 처리된 결제 입니다." } */
public record TossErrorResponse(
        String code,
        String message
) {
}
