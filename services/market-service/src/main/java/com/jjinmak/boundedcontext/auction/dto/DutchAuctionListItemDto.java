package com.jjinmak.boundedcontext.auction.dto;

import com.jjinmak.boundedcontext.auction.domain.AuctionStatus;
import com.jjinmak.boundedcontext.auction.domain.DutchAuction;
import com.jjinmak.boundedcontext.auction.domain.FailureReason;

import java.time.LocalDateTime;

public record DutchAuctionListItemDto(
        Long auctionId,
        Long productId,
        AuctionStatus status,
        Long startPrice,
        Long currentPrice,
        long dropAmount,
        int dropIntervalSec,
        LocalDateTime nextDropAt,
        boolean isLastPrice,
        LocalDateTime startAt,
        Long winningPrice,
        FailureReason failureReason
) {
    public static DutchAuctionListItemDto from(DutchAuction auction, LocalDateTime now) {
        boolean inProgress = auction.isInProgress();
        return new DutchAuctionListItemDto(
                auction.getId(),
                auction.getProductId(),
                auction.getStatus(),
                auction.getStartPrice(),
                inProgress ? auction.currentPriceAt(now) : auction.getWinningPrice(),
                auction.getDropAmount(),
                auction.getDropIntervalSec(),
                inProgress ? auction.nextDropAt(now) : null,
                inProgress && auction.isLastPriceAt(now),
                auction.getStartAt(),
                auction.getWinningPrice(),
                auction.getFailureReason()
        );
    }
}
