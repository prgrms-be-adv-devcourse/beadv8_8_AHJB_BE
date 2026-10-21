package com.jjinmak.back.boundedContext.product.domain;

// 경매 무산 사유. 경매 컨텍스트의 FailureReason과 같은 이름을 쓴다. (AuctionFailedEvent는 문자열로 전달)
public enum ProductAuctionFailureReason {
    NO_BID,     // 유찰
    UNPAID,     // 미결제
    REFUNDED;   // 환불

    public static ProductAuctionFailureReason from(String name) {
        return valueOf(name);
    }
}
