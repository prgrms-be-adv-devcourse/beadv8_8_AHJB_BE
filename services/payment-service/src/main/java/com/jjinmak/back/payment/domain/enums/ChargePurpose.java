package com.jjinmak.back.payment.domain.enums;

/**
 * 토스 결제 1건(Charge)의 용도.
 *
 * MANUAL         : 사용자가 직접 예치금을 충전. Payment 와 무관
 * FOR_PAYMENT    : 주문 결제 중 예치금이 부족해 부족분만 충전. 반드시 Payment 에 연결되며,
 *                  같은 트랜잭션에서 홀딩까지 끝나야 USED 가 된다
 * DIRECT_PAYMENT : 주문 대금을 결제창에서 직접 결제. 반드시 Payment 에 연결된다.
 *                  낸 돈은 예치금을 거치지 않고 바로 ESCROW 로 들어가며, 환불도 토스 취소로 돌려준다
 *
 * MANUAL · FOR_PAYMENT 는 예치금으로 들어오는 충전이라 계좌이체로만 받는다. (예치금 사용액에 포인트를 적립하므로 현금성 수단만 허용)
 * DIRECT_PAYMENT 는 사용자가 고른 수단(계좌이체 / 카드)으로 결제창을 열며, 용도만으로는 수단이 정해지지 않는다.
 */
public enum ChargePurpose {
    MANUAL(PgMethod.TRANSFER),
    FOR_PAYMENT(PgMethod.TRANSFER),
    DIRECT_PAYMENT(null);

    private final PgMethod requiredMethod;

    ChargePurpose(PgMethod requiredMethod) {
        this.requiredMethod = requiredMethod;
    }

    // 이 용도의 충전이 반드시 써야 하는 결제수단. 용도만으로 정해지지 않으면(직접 결제) null
    public PgMethod requiredMethod() {
        return requiredMethod;
    }

    // 결제창에서 낸 돈이 예치금으로 들어오는 용도인지
    public boolean isDepositCharge() {
        return this != DIRECT_PAYMENT;
    }

    // 주문 결제(Payment)에 묶인 용도인지
    public boolean isBoundToPayment() {
        return this != MANUAL;
    }
}
