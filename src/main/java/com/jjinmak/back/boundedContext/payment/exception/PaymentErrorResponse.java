package com.jjinmak.back.boundedContext.payment.exception;

import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.ErrorCode;

/**
 * 결제·지갑 API 의 에러 응답. 공통 ErrorResponse(isSuccess, errorCode, message) 에 결제 전용 정보를 더한다.
 *
 * - retryable : 사용자가 "다시 결제하기" 로 해결할 수 있는 실패인지. false 면 재시도해도 같은 결과이므로 안내 후 이동
 * - pgCode / pgMessage : 토스가 돌려준 실패 코드·메시지. PG 관련 에러가 아니면 null
 */
public record PaymentErrorResponse(
        boolean isSuccess,
        String errorCode,
        String message,
        boolean retryable,
        String pgCode,
        String pgMessage
) {
    public static PaymentErrorResponse from(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        boolean retryable = code instanceof PaymentErrorCode pc && pc.isRetryable();
        String pgCode = e instanceof PgApiException pg ? pg.getPgCode() : null;
        String pgMessage = e instanceof PgApiException pg ? pg.getPgMessage() : null;
        return new PaymentErrorResponse(false, code.getCode(), code.getMessage(), retryable, pgCode, pgMessage);
    }
}
