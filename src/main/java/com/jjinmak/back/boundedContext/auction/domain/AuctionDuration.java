package com.jjinmak.back.boundedContext.auction.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuctionDuration {
    ONE_DAY(1),
    THREE_DAYS(3),
    SEVEN_DAYS(7);
    private final int days;
}
