package com.jjinmak.back.boundedContext.payment.domain.enums;

/**
 * 충전의 용도.
 *
 * MANUAL      : 사용자가 직접 예치금을 충전. Payment 와 무관
 * FOR_PAYMENT : 주문 결제 중 예치금이 부족해 부족분만 충전. 반드시 Payment 에 연결되며,
 *               같은 트랜잭션에서 홀딩까지 끝나야 USED 가 된다
 */
public enum ChargePurpose {
    MANUAL,
    FOR_PAYMENT
}
