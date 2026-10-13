package com.jjinmak.back.boundedContext.wallet.domain.enums;

public enum HoldingStatus {
    HELD,       // 구매자 지갑에서 빠져나와 보관 중
    RELEASED,   // 구매 확정 → 판매자 정산 + 수수료 분배 완료
    REFUNDED    // 취소·환불 → 구매자 지갑으로 반환 완료
}
