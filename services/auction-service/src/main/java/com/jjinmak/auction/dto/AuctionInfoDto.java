package com.jjinmak.auction.dto;

import com.jjinmak.auction.domain.AuctionStatus;
import com.jjinmak.auction.domain.EnglishAuction;
import com.jjinmak.auction.domain.FailureReason;

import java.time.LocalDateTime;

public record AuctionInfoDto(
        Long auctionId,
        Long productId,
        Long startPrice,
        Long instantWinPrice,
        LocalDateTime startAt,
        AuctionStatus status,
        Long currentPrice,
        Long bidIncrement,
        Long minBidPrice,
        int bidCount,
        LocalDateTime endAt,
        Long winningPrice,
        FailureReason failureReason

) {
    public static AuctionInfoDto of(EnglishAuction auction, LocalDateTime now) {
        boolean inProgress = auction.isInProgress();
        return new AuctionInfoDto(
                auction.getId(),
                auction.getProductId(),
                auction.getStartPrice(),
                auction.getInstantWinPrice(),
                auction.getStartAt(),
                auction.getStatus(),
                auction.currentPriceAt(now),
                inProgress ? auction.getBidUnit() : null,
                inProgress ? auction.getMinBidPrice() : null,
                auction.getBidCount(),
                auction.getEndAt(),
                auction.getWinningPrice(),
                auction.getFailureReason()
        );
    }
}
