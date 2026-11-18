package com.jjinmak.back.payment.domain.enums;


public enum WalletTransactionType {

    // ===== 충전 =====
    CHARGE,                  // 충전: 외부 PG → USER (+)
    CHARGE_CANCEL,            // 충전 취소: USER (-) → 외부 PG

    // ===== 출금 =====
    WITHDRAWAL,              // 출금: USER (-) → 외부 계좌 (명목상, 실제 송금 없음)

    // ===== 결제 (홀딩) =====
    HOLD_PAY,                // 결제: 구매자 USER (-)
    HOLD_RECEIVE,            // 결제: ESCROW (+)

    // ===== 구매 확정 - 상품 판매 대금 =====
    SETTLEMENT_PAY,          // 구매 확정: ESCROW (-)
    SETTLEMENT_RECEIVE,      // 구매 확정: 판매자 USER (+)

    // ===== 구매 확정 - 상품 판매 수수료 =====
    FEE_PAY,                 // 구매 확정: ESCROW (-)
    FEE_RECEIVE,             // 구매 확정: FEE (+)

    // ===== 환불 =====
    REFUND_PAY,              // 환불: ESCROW (-)
    REFUND_RECEIVE,          // 환불: 구매자 USER (+)

    // ===== 포인트 =====
    POINT_EARN,              // 포인트 적립: 플랫폼 → USER 포인트 (+). 상대가 원장 밖이라 한 줄
    POINT_HOLD_PAY,          // 포인트 결제: 구매자 USER 포인트 (-)
    POINT_HOLD_RECEIVE,      // 포인트 결제: ESCROW (+)
    POINT_REFUND_PAY,        // 포인트 환불: ESCROW (-)
    POINT_REFUND_RECEIVE,    // 포인트 환불: 구매자 USER 포인트 (+)

    // ===== 수정 =====
    REVERSAL                 // 수정: 잘못된 기록 수정 (+), (-)
}

