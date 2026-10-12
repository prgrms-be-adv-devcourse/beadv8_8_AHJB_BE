package com.jjinmak.back.boundedContext.wallet.domain.enums;

/**
 * 원장(WalletTransaction)에 기록되는 거래 유형.
 * 외부 입출금(CHARGE, WITHDRAWAL)을 제외한 유형은
 * 같은 transactionGroupId 안에서 금액 합이 항상 0이 되어야 한다.
 */

public enum WalletTransactionType {

    // ===== 충전 =====
    CHARGE,          // 충전: 외부 PG → USER (+)

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
    REFUND_RECEIVE           // 환불: 구매자 USER (+)
}

