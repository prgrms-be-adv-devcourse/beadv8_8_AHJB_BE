package com.jjinmak.auction.app;

import com.jjinmak.auction.domain.DutchAuction;
import com.jjinmak.auction.dto.DutchBuyResponseDto;
import com.jjinmak.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.auction.exception.AuctionErrorCode.*;

@Service
@RequiredArgsConstructor
public class AuctionDutchBuyUseCase {
    private final AuctionReader auctionReader;
    private final AuctionEventPublisher auctionEventPublisher;

    public DutchBuyResponseDto buy(Long auctionId, Long memberId, Long expectedPrice, LocalDateTime now) {
        DutchAuction auction = auctionReader.getDutch(auctionId);
        validateBuy(auction, memberId, expectedPrice, now);
        auction.buy(memberId, expectedPrice, now);
        auctionEventPublisher.publishWon(auction);   // AFTER_COMMIT: 락 충돌로 롤백되면 발행 안 됨

        return new DutchBuyResponseDto(auction.getId(), auction.getWinningPrice(), auction.getWinAt(), auction.getStatus());
    }
    private void validateBuy(DutchAuction auction, Long memberId, Long expectedPrice, LocalDateTime now) {
        if (auction.isSold()) {
            throw new BusinessException(DUTCH_ALREADY_SOLD);        // 남이 먼저 샀음 (경로 ②)
        }
        if (!auction.isInProgress()) {
            throw new BusinessException(AUCTION_NOT_IN_PROGRESS);   // 유찰 등
        }
        if (auction.isEnded(now)) {
            throw new BusinessException(AUCTION_ALREADY_ENDED);     // 종료 시각 지남, 스케줄러 처리 전
        }
        if (auction.isSeller(memberId)) {
            throw new BusinessException(SELLER_CANNOT_BID);
        }
        if (auction.currentPriceAt(now) > expectedPrice) {
            throw new BusinessException(DUTCH_PRICE_CHANGED);       // 클라이언트 시계가 빠른 경우
        }
    }
}