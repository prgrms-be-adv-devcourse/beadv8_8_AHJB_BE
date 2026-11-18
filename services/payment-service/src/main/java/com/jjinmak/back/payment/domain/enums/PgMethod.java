package com.jjinmak.back.payment.domain.enums;

/**
 * 토스 결제창을 열 때 지정하는 결제수단.
 *
 * - name()   : 결제창(SDK requestPayment)의 method 값. 클라이언트가 이 값으로 결제창을 연다
 * - tossName : 토스 승인 응답(Payment 객체)의 method 표기. 실제로 어떤 수단으로 결제됐는지 확인할 때 쓴다
 *
 * 클라이언트가 보낸 값은 바뀔 수 있으므로, 결제수단은 항상 토스 승인 응답으로 확인한다.
 */
public enum PgMethod {
    TRANSFER("계좌이체");

    private final String tossName;

    PgMethod(String tossName) {
        this.tossName = tossName;
    }

    public String getTossName() {
        return tossName;
    }

    // 토스 승인 응답의 method 가 이 결제수단인지
    public boolean matches(String tossMethod) {
        return tossName.equals(tossMethod) || name().equalsIgnoreCase(tossMethod);
    }
}
