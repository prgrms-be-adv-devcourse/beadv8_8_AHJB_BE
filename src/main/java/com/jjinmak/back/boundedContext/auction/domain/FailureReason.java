package com.jjinmak.back.boundedContext.auction.domain;

public enum FailureReason {
    CANCELED,   // 경매취소
    NO_BID,     // 유찰
    UNPAID,     // 미결제
    REFUNDED //환불
}
