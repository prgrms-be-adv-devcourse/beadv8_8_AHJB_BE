package com.jjinmak.auction.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static com.jjinmak.auction.domain.AuctionStatus.*;

@Getter
@RequiredArgsConstructor
public enum FailureReason {
    NO_BID(IN_PROGRESS),     // 유찰
    UNPAID(WON),     // 미결제
    REFUNDED(PAID); //환불
    private final AuctionStatus from;
}
