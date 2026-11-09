package com.jjinmak.auction.dto;

import com.jjinmak.auction.domain.AuctionType;
import com.jjinmak.auction.domain.DutchAuction;
import com.jjinmak.auction.domain.FailureReason;
import com.jjinmak.auction.domain.AuctionStatus;

import java.time.LocalDateTime;

// 하향식 상세의 공용 값. floorPrice·endAt은 비공개라 필드 자체가 없다 (R4, R5)
public record DutchAuctionInfoDto(
        AuctionType auctionType,
        Long auctionId,
        Long productId,
        Long startPrice,
        LocalDateTime startAt,
        AuctionStatus status,
        Long currentPrice,          // 진행 중: 지금 가격 / 판매됨: 낙찰가 / 유찰: null
        Long nextPrice,             // 마지막 단계·종료 후 null
        LocalDateTime nextDropAt,   // 마지막 단계·종료 후 null
        boolean isLastPrice,        // 마지막 단계면 true (D-11)
        long dropAmount,
        int dropIntervalSec,
        Long winningPrice,
        FailureReason failureReason
) {
    public static DutchAuctionInfoDto of(DutchAuction auction, LocalDateTime now) {
        boolean inProgress = auction.isInProgress();
        return new DutchAuctionInfoDto(
                AuctionType.DUTCH,
                auction.getId(),
                auction.getProductId(),
                auction.getStartPrice(),
                auction.getStartAt(),
                auction.getStatus(),
                inProgress ? auction.currentPriceAt(now) : auction.getWinningPrice(),
                inProgress ? auction.nextPriceAt(now) : null,
                inProgress ? auction.nextDropAt(now) : null,
                inProgress && auction.isLastPriceAt(now),
                auction.getDropAmount(),
                auction.getDropIntervalSec(),
                auction.getWinningPrice(),
                auction.getFailureReason()
        );
    }
}