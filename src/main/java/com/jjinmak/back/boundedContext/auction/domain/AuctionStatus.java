package com.jjinmak.back.boundedContext.auction.domain;


public enum AuctionStatus {
    READY,      // 시작대기
    IN_PROGRESS, // 진행중
    WON, // 낙찰완료
    PAID, // 결제완료
    COMPLETED, //경매완료
    FAILED; // 경매무산
}
