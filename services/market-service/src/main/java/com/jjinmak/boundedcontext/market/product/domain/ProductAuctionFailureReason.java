package com.jjinmak.boundedcontext.market.product.domain;

// 상품 화면에 보여 주는 경매 무산 사유. 경매 컨텍스트의 FailureReason과 같은 이름을 쓴다.
public enum ProductAuctionFailureReason {
    NO_BID,     // 유찰
    UNPAID,     // 미결제
    REFUNDED;   // 환불

    public static ProductAuctionFailureReason from(String name) {
        return valueOf(name);
    }
}
