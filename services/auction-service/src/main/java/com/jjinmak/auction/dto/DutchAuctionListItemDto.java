package com.jjinmak.auction.dto;

import com.jjinmak.auction.domain.AuctionStatus;
import com.jjinmak.auction.domain.DutchAuction;
import com.jjinmak.auction.domain.FailureReason;
import com.jjinmak.auction.domain.ProductSnapshot;
import java.time.LocalDateTime;

public record DutchAuctionListItemDto(
        Long auctionId,
        Long productId,
        String productName,
        String thumbnailUrl,
        AuctionStatus status,
        Long startPrice,
        Long currentPrice,          // 진행 중: 응답 시점 가격 / 판매됨: 낙찰가 / 유찰: null
        long dropAmount,
        int dropIntervalSec,
        LocalDateTime nextDropAt,
        boolean isLastPrice,
        LocalDateTime startAt,
        Long winningPrice,
        FailureReason failureReason
) {
    public static DutchAuctionListItemDto from(DutchAuction auction, LocalDateTime now) {
        ProductSnapshot snapshot = auction.getProductSnapshot();
        boolean inProgress = auction.isInProgress();
        return new DutchAuctionListItemDto(
                auction.getId(),
                auction.getProductId(),
                snapshot != null ? snapshot.getProductName() : null,
                snapshot != null ? snapshot.getThumbnailUrl() : null,
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