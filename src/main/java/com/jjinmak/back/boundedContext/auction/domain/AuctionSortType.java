package com.jjinmak.back.boundedContext.auction.domain;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.domain.Sort.Direction.DESC;


public enum AuctionSortType {
    ENDING_SOON(Sort.by(ASC, "endAt")),          // 마감 임박순 (진행 중 기본)
    RECENTLY_ENDED(Sort.by(DESC, "endAt")),      // 최근 종료순 (종료 기본)
    NEWEST(Sort.by(DESC, "startAt")),            // 최신 등록순
    MOST_BIDS(Sort.by(DESC, "bidCount")),        // 입찰 많은순
    PRICE_ASC(JpaSort.unsafe(ASC, "coalesce(a.highestBidPrice, a.startPrice)")),
    PRICE_DESC(JpaSort.unsafe(DESC, "coalesce(a.highestBidPrice, a.startPrice)"));

    private final Sort sort;

    AuctionSortType(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort.and(Sort.by(DESC, "id"));
    }
}