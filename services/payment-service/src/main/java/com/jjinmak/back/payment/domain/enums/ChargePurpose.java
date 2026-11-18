package com.jjinmak.back.payment.domain.enums;

/**
 * 충전의 용도.
 *
 * MANUAL      : 사용자가 직접 예치금을 충전. Payment 와 무관
 * FOR_PAYMENT : 주문 결제 중 예치금이 부족해 부족분만 충전. 반드시 Payment 에 연결되며,
 *               같은 트랜잭션에서 홀딩까지 끝나야 USED 가 된다
 *
 * 둘 다 예치금으로 들어오는 충전이라 계좌이체로만 받는다. (예치금 사용액에 포인트를 적립하므로 현금성 수단만 허용)
 */
public enum ChargePurpose {
    MANUAL(PgMethod.TRANSFER),
    FOR_PAYMENT(PgMethod.TRANSFER);

    private final PgMethod requiredMethod;

    ChargePurpose(PgMethod requiredMethod) {
        this.requiredMethod = requiredMethod;
    }

    // 이 용도의 충전이 반드시 써야 하는 결제수단
    public PgMethod requiredMethod() {
        return requiredMethod;
    }
}
