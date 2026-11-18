package com.jjinmak.back.boundedContext.product.domain;

// 상품이 복제해 두는 경매 상태. 경매 이벤트(시작/낙찰/무산)로만 바뀐다.
// 결제완료·구매확정은 주문의 관심사라 상품은 구분하지 않는다. (낙찰 후 거래가 깨지면 무산 이벤트로 FAILED가 된다)
public enum ProductAuctionStatus {
    SCHEDULED,      // 시작 전 (수정 기간 또는 경매 생성 대기)
    IN_PROGRESS,    // 진행 중
    SOLD,           // 낙찰
    FAILED          // 무산 (failureReason 참고)
}
