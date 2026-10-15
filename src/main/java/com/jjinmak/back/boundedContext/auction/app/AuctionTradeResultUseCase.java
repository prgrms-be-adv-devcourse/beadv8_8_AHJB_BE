package com.jjinmak.back.boundedContext.auction.app;

import com.jjinmak.back.boundedContext.auction.domain.Auction;
import com.jjinmak.back.boundedContext.auction.domain.AuctionStatus;
import com.jjinmak.back.boundedContext.auction.domain.FailureReason;
import com.jjinmak.back.boundedContext.auction.out.AuctionEventPublisher;
import com.jjinmak.back.boundedContext.auction.out.AuctionRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.back.boundedContext.auction.domain.AuctionStatus.*;
import static com.jjinmak.back.boundedContext.auction.domain.FailureReason.*;
import static com.jjinmak.back.boundedContext.auction.exception.AuctionErrorCode.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionTradeResultUseCase {
    private final AuctionRepository auctionRepository;
    private final AuctionEventPublisher auctionEventPublisher;

    public void applyPaymentSucceeded(Long auctionId){
        Auction auction = getAuction(auctionId);
        if(isDuplicate(auction,PAID,"결제완료")){return;}
        auction.pay();
    }
    public void applyPaymentDefaulted(Long auctionId, LocalDateTime now){
        failAndPublish(auctionId, UNPAID,now,"미결제");
    }
    public void applyPurchaseConfirmed(Long auctionId){
        Auction auction = getAuction(auctionId);
        if(isDuplicate(auction,COMPLETED,"구매확정")){return;}
        auction.complete();
    }
    public void applyRefundAccepted(Long auctionId,LocalDateTime now){
        failAndPublish(auctionId,REFUNDED,now,"환불완료");
    }
    private void failAndPublish(Long auctionId,FailureReason reason,LocalDateTime now,String eventName){
        Auction auction = getAuction(auctionId);
        if(isDuplicate(auction,FAILED,eventName)){return;}
        auction.fail(reason);
        auctionEventPublisher.publishFailed(auction,now);
    }
    private Auction getAuction(Long auctionId){
        return auctionRepository.findById(auctionId).orElseThrow(()->new BusinessException(AUCTION_NOT_FOUND));
    }
    private boolean isDuplicate(Auction auction, AuctionStatus result,String eventName){
        if(auction.isStatus(result)){
            log.info("{} 이벤트 중복 수신, 무시: auctionId={}", eventName,auction.getId());
            return true;
        }
        return false;
    }
}
