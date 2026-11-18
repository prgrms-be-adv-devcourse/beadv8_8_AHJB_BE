package com.jjinmak.back.payment.domain.enums;

import java.util.List;

/**
 * 토스 결제창을 열 때 지정하는 결제수단.
 *
 * - name()    : 결제창(SDK requestPayment)의 method 값. 클라이언트가 이 값으로 결제창을 연다
 * - tossNames : 토스 승인 응답(Payment 객체)의 method 표기. 실제로 어떤 수단으로 결제됐는지 확인할 때 쓴다
 *
 * TRANSFER : 계좌이체
 * CARD     : 카드·간편결제 통합 결제창. 승인 응답은 카드 또는 간편결제로 온다
 *
 * 클라이언트가 보낸 값은 바뀔 수 있으므로, 결제수단은 항상 토스 승인 응답으로 확인한다.
 */
public enum PgMethod {
    TRANSFER("계좌이체"),
    CARD("카드", "간편결제");

    private final List<String> tossNames;

    PgMethod(String... tossNames) {
        this.tossNames = List.of(tossNames);
    }

    // 토스 승인 응답의 대표 표기
    public String getTossName() {
        return tossNames.get(0);
    }

    // 토스 승인 응답의 method 가 이 결제수단인지
    public boolean matches(String tossMethod) {
        return tossMethod != null && (tossNames.contains(tossMethod) || name().equalsIgnoreCase(tossMethod));
    }
}
