package com.jjinmak.back.boundedContext.payment.domain.enums;

/**
 * PG(토스페이먼츠) 충전 1건의 상태.
 *
 * REQUESTED : 결제창을 띄우기 위해 Charge 를 만들고 pgOrderId 를 발급한 상태. 아직 승인 전
 * CHARGED   : 토스 승인 성공 + 지갑 원장에 CHARGE 기록 완료.
 *             MANUAL 충전은 여기가 정상 종료 상태.
 *             FOR_PAYMENT 충전이 여기에 머물러 있으면 "돈은 들어왔는데 주문 결제(홀딩)가 끝나지 않은" 건이다
 * USED      : FOR_PAYMENT 충전이 해당 주문의 홀딩에 소비됨. 정상 종료
 * CANCELED  : 충전 취소 (토스 취소 + 지갑 CHARGE_CANCEL)
 * ABORTED   : 토스 승인 실패, 또는 인증 후 승인되지 않아 만료됨
 */
public enum ChargeStatus {
    REQUESTED,
    CHARGED,
    USED,
    CANCELED,
    ABORTED;

    public boolean isTerminal() {
        return this == USED || this == CANCELED || this == ABORTED;
    }
}
