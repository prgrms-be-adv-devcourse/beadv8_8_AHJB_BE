package com.jjinmak.back.boundedContext.auction.domain;

import java.util.List;

public enum AuctionListStatus {
    IN_PROGRESS(List.of(AuctionStatus.IN_PROGRESS), AuctionSortType.ENDING_SOON),
    ENDED(List.of(AuctionStatus.WON, AuctionStatus.PAID, AuctionStatus.COMPLETED, AuctionStatus.FAILED),
            AuctionSortType.RECENTLY_ENDED);

    private final List<AuctionStatus> statuses;
    private final AuctionSortType defaultSort;

    AuctionListStatus(List<AuctionStatus> statuses, AuctionSortType defaultSort) {
        this.statuses = statuses;
        this.defaultSort = defaultSort;
    }

    public List<AuctionStatus> statuses() {
        return statuses;
    }

    public AuctionSortType defaultSort() {
        return defaultSort;
    }
}