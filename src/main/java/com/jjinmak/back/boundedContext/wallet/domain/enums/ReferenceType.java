package com.jjinmak.back.boundedContext.wallet.domain.enums;

/**
 * 원장 기록이 어떤 도메인 객체 때문에 발생했는지 가리키는 타입.
 * 다른 컨텍스트의 테이블에는 FK를 걸지 않고 (referenceType, referenceId) 쌍으로만 기록한다.
 */

public enum ReferenceType {
    PAYMENT,     // payment 컨텍스트의 Charge/Payment
    ORDER,       // order 컨텍스트의 Order
    HOLDING     // wallet 컨텍스트의 Holding
}
