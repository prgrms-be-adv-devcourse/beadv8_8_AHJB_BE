package com.jjinmak.back.boundedContext.payment.exception;

import com.jjinmak.back.boundedContext.payment.in.PaymentApiController;
import com.jjinmak.back.boundedContext.payment.in.WalletApiController;
import com.jjinmak.back.global.exception.BusinessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 결제·지갑 컨트롤러에서 난 BusinessException 을 PaymentErrorResponse 로 바꾼다.
 * 다른 컨트롤러는 GlobalExceptionHandler 가 그대로 처리한다 (assignableTypes 로 범위 제한).
 * 응답 형태는 공통 ErrorResponse 의 상위 호환이라 프론트의 기존 에러 처리는 그대로 동작한다.
 */
@RestControllerAdvice(assignableTypes = {PaymentApiController.class, WalletApiController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaymentExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<PaymentErrorResponse> handle(BusinessException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus()).body(PaymentErrorResponse.from(e));
    }
}
