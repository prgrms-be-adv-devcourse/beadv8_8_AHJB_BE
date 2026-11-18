package com.jjinmak.back.payment.domain.enums;

/**
 * 주문 묶음(OrderGroup) 1건에 대한 결제 처리 상태.
 *
 * PENDING  : 주문 호출을 받아 Payment 를 만든 직후. 아직 사용자가 결제 요청을 보내지 않음
 * CHARGING : 결제창을 거쳐야 해서 기다리는 중 (Charge 가 REQUESTED 상태).
 *            예치금이 부족해 부족분을 충전하는 경우와 직접 결제(계좌이체 · 카드)하는 경우 모두 여기에 해당한다
 * PAID     : 총액이 포인트 · 예치금 · 직접 결제로 ESCROW 에 들어와 주문별 Holding 이 만들어짐. 정상 종료
 * FAILED   : 돈이 움직이기 전에 결제가 닫힘. 주문이 주문 그룹을 버렸을 때 이 상태가 된다 (PaymentCloseUseCase, 사유는 failureReason).
 *            이후 승인과 같은 orderGroupId 의 결제 요청은 PAYMENT_CLOSED 로 거절된다.
 *            PG 승인 실패는 결제창(Charge)만 ABORTED 로 닫고 결제는 CHARGING 에 남겨 다시 결제할 수 있게 한다
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
