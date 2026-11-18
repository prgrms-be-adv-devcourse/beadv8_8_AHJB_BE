package com.jjinmak.boundedcontext.auction.dto;

import com.jjinmak.boundedcontext.auction.domain.AuctionStatus;
import com.jjinmak.boundedcontext.auction.domain.DutchAuction;
import com.jjinmak.boundedcontext.auction.domain.FailureReason;
import com.jjinmak.contracts.product.dto.ProductSummaryDto;

import java.time.LocalDateTime;

public record DutchAuctionListItemDto(
        Long auctionId,
        Long productId,
        String productName,     // 상품을 찾지 못하면 null
        String thumbnailUrl,    // 대표 이미지가 없으면 null
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
    public static DutchAuctionListItemDto from(DutchAuction auction, ProductSummaryDto product, LocalDateTime now) {
        boolean inProgress = auction.isInProgress();
        return new DutchAuctionListItemDto(
                auction.getId(),
                auction.getProductId(),
                product != null ? product.name() : null,
                product != null ? product.thumbnailUrl() : null,
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
