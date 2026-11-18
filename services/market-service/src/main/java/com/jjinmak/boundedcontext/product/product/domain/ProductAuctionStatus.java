package com.jjinmak.boundedcontext.product.product.domain;

// 상품 화면(내 상품)에 보여 주는 경매 상태. 저장하지 않고 경매 상태에서 바꿔 만든다. (ProductAuctionStateReader)
// 결제완료·구매확정은 주문의 관심사라 상품은 구분하지 않는다. (낙찰 후 거래가 깨지면 경매가 FAILED가 된다)
public enum ProductAuctionStatus {
    SCHEDULED,      // 시작 전 (수정 기간 또는 경매 생성 대기)
    IN_PROGRESS,    // 진행 중
    SOLD,           // 낙찰
    FAILED          // 무산 (failureReason 참고)
}
