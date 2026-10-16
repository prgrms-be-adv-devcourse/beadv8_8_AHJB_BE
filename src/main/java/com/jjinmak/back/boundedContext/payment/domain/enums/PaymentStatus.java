package com.jjinmak.back.boundedContext.payment.domain.enums;

/**
 * 주문 묶음(OrderGroup) 1건에 대한 결제 처리 상태.
 *
 * PENDING  : 주문 호출을 받아 Payment 를 만든 직후. 아직 사용자가 결제 요청을 보내지 않음
 * CHARGING : 예치금이 부족해 부족분을 PG 로 충전하는 중 (Charge 가 REQUESTED 상태)
 * PAID     : 구매자 지갑에서 총액이 빠져나가 주문별 Holding 이 만들어짐. 정상 종료
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
