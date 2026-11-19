package com.jjinmak.back.payment.domain.enums;

import com.jjinmak.common.exception.BusinessException;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_METHOD_NOT_SUPPORTED;

/**
 * 주문 결제에서 포인트로 내고 남은 금액을 무엇으로 낼지. 사용자가 주문할 때 고른다.
 *
 * DEPOSIT  : 예치금. 모자라면 부족분을 계좌이체로 충전한 뒤 예치금에서 낸다
 * TRANSFER : 계좌이체 직접 결제. 결제창에서 낸 돈이 예치금을 거치지 않고 바로 주문 대금이 된다
 * CARD     : 카드·간편결제 직접 결제. 위와 같다
 *
 * 포인트는 어느 수단과도 함께 쓸 수 있다. 예치금과 직접 결제는 함께 쓸 수 없다
 */
public enum PayMethod {
    DEPOSIT(null),
    TRANSFER(PgMethod.TRANSFER),
    CARD(PgMethod.CARD);

    // 직접 결제일 때 결제창을 여는 결제수단. 예치금 결제면 null
    private final PgMethod pgMethod;

    PayMethod(PgMethod pgMethod) {
        this.pgMethod = pgMethod;
    }

    public PgMethod pgMethod() {
        return pgMethod;
    }

    // 결제창에서 낸 돈이 예치금을 거치지 않고 주문 대금이 되는 수단인지
    public boolean isDirect() {
        return pgMethod != null;
    }

    // 요청으로 받은 값을 해석한다. 값이 없으면 예치금 결제 (결제수단을 보내지 않는 기존 요청과의 호환)
    public static PayMethod from(String value) {
        if (value == null || value.isBlank()) {
            return DEPOSIT;
        }
        for (PayMethod method : values()) {
            if (method.name().equalsIgnoreCase(value.trim())) {
                return method;
            }
        }
        throw new BusinessException(PAYMENT_METHOD_NOT_SUPPORTED);
    }
}
