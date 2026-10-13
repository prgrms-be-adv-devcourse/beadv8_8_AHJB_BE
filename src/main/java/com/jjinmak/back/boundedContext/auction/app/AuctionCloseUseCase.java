package com.jjinmak.back.boundedContext.auction.app;

import com.jjinmak.back.boundedContext.auction.domain.Auction;
import com.jjinmak.back.boundedContext.auction.domain.AuctionCloseResult;
import com.jjinmak.back.boundedContext.auction.domain.AuctionStatus;
import com.jjinmak.back.boundedContext.auction.domain.FailureReason;
import com.jjinmak.back.boundedContext.auction.out.AuctionRepository;
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
    public AuctionCloseResult closeAuction(Long auctionId,LocalDateTime now){
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if(auction==null|| auction.getStatus()!=AuctionStatus.IN_PROGRESS||!auction.isEnded(now)){
            return AuctionCloseResult.SKIPPED;
        }
        //로그
        if(auction.hasBids()){
            auction.win(auction.getHighestBidderId(),auction.getHighestBidPrice(),now);
            auctionEventPublisher.publishWon(auction);
            return AuctionCloseResult.WON;
        }
        auction.fail(FailureReason.NO_BID);
        auctionEventPublisher.publishFailed(auction,now);
        return AuctionCloseResult.FAILED;
    }
}
