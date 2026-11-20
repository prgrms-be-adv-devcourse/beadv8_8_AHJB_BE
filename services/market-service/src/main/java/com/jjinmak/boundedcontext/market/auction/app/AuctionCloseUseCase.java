package com.jjinmak.boundedcontext.market.auction.app;

import com.jjinmak.boundedcontext.market.auction.domain.Auction;
import com.jjinmak.boundedcontext.market.auction.domain.AuctionCloseResult;
import com.jjinmak.boundedcontext.market.auction.domain.AuctionStatus;
import com.jjinmak.boundedcontext.market.auction.domain.FailureReason;
import com.jjinmak.boundedcontext.market.auction.out.AuctionBulkCloseRepository;
import com.jjinmak.boundedcontext.market.auction.out.AuctionBulkCloseRepository.FailedAuction;
import com.jjinmak.boundedcontext.market.auction.out.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuctionCloseUseCase {
    private final AuctionRepository auctionRepository;
    private final AuctionEventPublisher auctionEventPublisher;
    private final AuctionBulkCloseRepository auctionBulkCloseRepository;

    // 입찰 없는 경매를 UPDATE 한 번으로 유찰하고, 유찰된 경매마다 유찰 이벤트를 발행한다 (#516)
    // 이벤트는 같은 트랜잭션에서 outbox에 저장되고(#548) 릴레이가 보낸다. 건별 처리와 같은 경로
    public int failNoBidAuctions(LocalDateTime now, int batchSize) {
        List<FailedAuction> failed = auctionBulkCloseRepository.failNoBidAuctions(now, LocalDateTime.now(), batchSize);
        for (FailedAuction f : failed) {
            auctionEventPublisher.publishFailed(f.auctionId(), f.productId(), f.sellerId(), FailureReason.NO_BID, now);
        }
        return failed.size();
    }

    public List<Long> findCloseTargetIds(LocalDateTime now, int batchsize){
        return auctionRepository.findCloseTargetIds(
                AuctionStatus.IN_PROGRESS,now, PageRequest.of(0,batchsize));
    }
    public AuctionCloseResult closeAuction(Long auctionId, LocalDateTime now) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null || !auction.isInProgress() || !auction.isEnded(now)) {
            return AuctionCloseResult.SKIPPED;
        }
        AuctionCloseResult result = auction.close(now);
        if (result == AuctionCloseResult.WON) {
            auctionEventPublisher.publishWon(auction);
        } else {
            auctionEventPublisher.publishFailed(auction, now);
        }
        return result;
    }
}
