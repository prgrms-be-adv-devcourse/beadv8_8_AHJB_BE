package com.jjinmak.back.boundedContext.auction.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BidIncrement {
    FROM_5_000_000(5_000_000L, 100_000L),
    FROM_1_000_000(1_000_000L,  50_000L),
    FROM_500_000  (  500_000L,  10_000L),
    FROM_100_000  (  100_000L,   5_000L),
    FROM_10_000   (   10_000L,   1_000L),
    FROM_0        (        0L,     500L);

    private final long minPrice;
    private final long unit;
    public static long unitOf(long currentPrice){
        for(BidIncrement increment : values()){
            if(currentPrice>=increment.minPrice){
                return increment.unit;
            }
        }
        throw new IllegalArgumentException("가격은 0원 이상이어야 합니다."+currentPrice);
    }
}
