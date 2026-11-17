package com.jjinmak.auction.app;

import com.jjinmak.auction.domain.Auction;
import com.jjinmak.auction.domain.AuctionCloseResult;
import com.jjinmak.auction.domain.AuctionStatus;
import com.jjinmak.auction.out.AuctionRepository;
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
