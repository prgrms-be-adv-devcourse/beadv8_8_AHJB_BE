package com.jjinmak.back.boundedContext.payment.exception;

import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.ErrorCode;
import lombok.Getter;

/**
 * 토스페이먼츠 API 가 에러를 돌려줬을 때. 토스의 code / message 를 보존해 Charge 에 기록하거나 로그에 남긴다.
 * BusinessException 을 상속하므로 GlobalExceptionHandler 가 그대로 처리한다.
 */
@Getter
public class PgApiException extends BusinessException {

    private final String pgCode;
    private final String pgMessage;

    public PgApiException(ErrorCode errorCode, String pgCode, String pgMessage) {
        super(errorCode);
        this.pgCode = pgCode;
        this.pgMessage = pgMessage;
    }

    public boolean isCode(String code) {
        return code.equals(pgCode);
    }

    @Override
    public String getMessage() {
        return "[%s] %s (toss: %s - %s)".formatted(getErrorCode().getCode(), getErrorCode().getMessage(), pgCode, pgMessage);
    }
}
