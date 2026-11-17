package com.jjinmak.boundedcontext.product.product.domain;

// 경매 방식
public enum AuctionType {
    ENGLISH, // 영국식: 판매자가 등록, 입찰가가 올라간다
    DUTCH    // 네덜란드식: 관리자가 이벤트로 등록, 가격이 일정하게 내려가고 먼저 산 사람이 낙찰
}
