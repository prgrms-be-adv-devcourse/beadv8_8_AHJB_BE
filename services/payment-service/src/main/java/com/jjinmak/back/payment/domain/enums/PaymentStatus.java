package com.jjinmak.back.payment.domain.enums;

/**
 * 주문 묶음(OrderGroup) 1건에 대한 결제 처리 상태.
 *
 * PENDING  : 주문 호출을 받아 Payment 를 만든 직후. 아직 사용자가 결제 요청을 보내지 않음
 * CHARGING : 결제창을 거쳐야 해서 기다리는 중 (Charge 가 REQUESTED 상태).
 *            예치금이 부족해 부족분을 충전하는 경우와 직접 결제(계좌이체 · 카드)하는 경우 모두 여기에 해당한다
 * PAID     : 총액이 포인트 · 예치금 · 직접 결제로 ESCROW 에 들어와 주문별 Holding 이 만들어짐. 정상 종료
 * FAILED   : PG 승인 실패 등으로 결제가 끝내 되지 않음.
 */
public enum PaymentStatus {
    PENDING,
    CHARGING,
    PAID,
    FAILED;

    public boolean isTerminal() {
        return this == PAID || this == FAILED;
    }
}
