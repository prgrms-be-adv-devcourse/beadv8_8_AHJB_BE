package com.jjinmak.back.boundedContext.payment.exception;

import com.jjinmak.back.global.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Payment / PaymentItem / Charge 가 던지는 에러 코드.
 * 지갑·결제 컨텍스트가 하나로 합쳐졌으므로 추후 WalletErrorCode 와 한 enum 으로 합치는 것을 전제로 한다.
 *
 * retryable : 사용자가 "다시 결제하기"(결제 요청부터 다시) 로 해결할 수 있는 실패인지. 에러 응답에 실려 프론트가 분기한다.
 *   - true  : 토스 거절·통신 불가·승인 불일치, 충전 요청이 이미 닫힌 경우 → 새 충전 요청으로 다시 시도 가능
 *   - false : 권한·금액 위변조·존재하지 않음 등 재시도해도 같은 결과
 */
@Getter
public enum PaymentErrorCode implements ErrorCode {

    // ===== 주문 결제 (Payment) =====
    PAYMENT_NOT_FOUND("PAYMENT001", "존재하지 않는 결제입니다.", HttpStatus.NOT_FOUND),
    PAYMENT_ALREADY_EXISTS("PAYMENT002", "이미 결제가 존재하는 주문 그룹입니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_ITEMS_EMPTY("PAYMENT003", "결제할 주문 내역이 없습니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_AMOUNT_MISMATCH("PAYMENT004", "주문 금액 합이 결제 총액과 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_STATUS("PAYMENT005", "현재 상태에서는 처리할 수 없는 결제입니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_ITEM_NOT_FOUND("PAYMENT006", "결제에 포함되지 않은 주문입니다.", HttpStatus.NOT_FOUND),
    INVALID_PAYMENT_AMOUNT("PAYMENT007", "결제 금액은 0보다 커야 합니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_FORBIDDEN("PAYMENT008", "결제에 대한 권한이 없습니다.", HttpStatus.FORBIDDEN),

    // ===== 충전 (Charge) =====
    CHARGE_NOT_FOUND("CHARGE001", "존재하지 않는 충전 건입니다.", HttpStatus.NOT_FOUND),
    INVALID_CHARGE_AMOUNT("CHARGE002", "충전 금액은 0보다 커야 합니다.", HttpStatus.BAD_REQUEST),
    CHARGE_AMOUNT_MISMATCH("CHARGE003", "요청 금액이 충전 요청 시 금액과 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    INVALID_CHARGE_STATUS("CHARGE004", "현재 상태에서는 처리할 수 없는 충전 건입니다.", HttpStatus.BAD_REQUEST, true),
    CHARGE_NOT_CANCELABLE("CHARGE005", "취소할 수 없는 충전 건입니다.", HttpStatus.BAD_REQUEST),
    CHARGE_PAYMENT_REQUIRED("CHARGE006", "주문 결제용 충전은 Payment 에 연결되어야 합니다.", HttpStatus.BAD_REQUEST),
    CHARGE_FORBIDDEN("CHARGE007", "충전 건에 대한 권한이 없습니다.", HttpStatus.FORBIDDEN),
    CHARGE_PAYMENT_MISMATCH("CHARGE008", "이 충전 건은 해당 결제의 것이 아닙니다.", HttpStatus.BAD_REQUEST),

    // ===== PG (토스페이먼츠) =====
    PG_APPROVE_FAILED("PG001", "PG 결제 승인에 실패했습니다.", HttpStatus.BAD_GATEWAY, true),
    PG_CANCEL_FAILED("PG002", "PG 결제 취소에 실패했습니다.", HttpStatus.BAD_GATEWAY, true),
    PG_INQUIRY_FAILED("PG003", "PG 결제 조회에 실패했습니다.", HttpStatus.BAD_GATEWAY, true),
    PG_UNAVAILABLE("PG004", "PG 서버와 통신할 수 없습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.SERVICE_UNAVAILABLE, true),
    PG_NOT_APPROVED("PG005", "PG 에서 승인되지 않은 결제입니다.", HttpStatus.BAD_REQUEST, true)
    ;

    private final String code;
    private final String message;
    private final HttpStatus status;
    private final boolean retryable;

    PaymentErrorCode(String code, String message, HttpStatus status) {
        this(code, message, status, false);
    }

    PaymentErrorCode(String code, String message, HttpStatus status, boolean retryable) {
        this.code = code;
        this.message = message;
        this.status = status;
        this.retryable = retryable;
    }
}
