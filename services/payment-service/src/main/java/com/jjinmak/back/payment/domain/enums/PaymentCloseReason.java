package com.jjinmak.back.payment.domain.enums;

/**
 * 돈이 움직이기 전에 결제를 닫은 이유. Payment.failureReason 과 닫힌 Charge 의 failureCode / failureMessage 에 남는다.
 *
 * ORDER_DELETED   : 주문 그룹이 삭제됨 (결제 기한 만료 등. OrderGroupDeletedEvent)
 * CLOSED_BY_ORDER : 주문이 주문 그룹을 버리면서 결제 포트로 닫음 (결제창 닫힘 등. PaymentPort.closePayment)
 */
public enum PaymentCloseReason {
    ORDER_DELETED("ORDER_DELETED", "주문 그룹이 삭제되어 결제가 종료됨"),
    CLOSED_BY_ORDER("CLOSED_BY_ORDER", "주문이 결제를 중단해 결제가 종료됨");

    private final String code;
    private final String message;

    PaymentCloseReason(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
