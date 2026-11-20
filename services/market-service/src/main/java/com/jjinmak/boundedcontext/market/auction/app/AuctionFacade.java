package com.jjinmak.boundedcontext.market.auction.app;

import com.jjinmak.boundedcontext.market.auction.domain.*;
import com.jjinmak.boundedcontext.market.auction.dto.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuctionFacade {
    private final AuctionCreateUseCase auctionCreateUseCase;
    private final AuctionBidUseCase auctionBidUseCase;
    private final AuctionInstantWinUseCase auctionInstantWinUseCase;
    private final AuctionBidHistoryUseCase auctionBidHistoryUseCase;
    private final AuctionCloseUseCase auctionCloseUseCase;
    private final AuctionDetailUseCase auctionDetailUseCase;
    private final AuctionTradeResultUseCase auctionTradeResultUseCase;
    private final AuctionMyBidsUseCase auctionMyBidsUseCase;
    private final AuctionListUseCase auctionListUseCase;
    private final AuctionDutchBuyUseCase auctionDutchBuyUseCase;
    private final AuctionEndingSoonUseCase auctionEndingSoonUseCase;
    private final AuctionRelistCheckUseCase auctionRelistCheckUseCase;


    @Transactional
    public AuctionCreateResultDto createAuction(AuctionCreateDto dto){
        return auctionCreateUseCase.createAuction(dto);
    }

    // market 재등록 흐름(MarketRelistUseCase)의 트랜잭션에 참여한다.
    @Transactional
    public void validateRelistable(Long auctionId){
        auctionRelistCheckUseCase.validateRelistable(auctionId);
    }

    @Transactional
    public AuctionBidResponseDto bid(Long auctionId, Long bidderId, Long bidPrice){return auctionBidUseCase.bid(auctionId,bidderId,bidPrice,LocalDateTime.now());}

    @Transactional
    public AuctionInstantWinResponseDto instantWin(Long auctionId,Long bidderId){return auctionInstantWinUseCase.instantWin(auctionId,bidderId,LocalDateTime.now());}

    @Transactional(readOnly = true)
    public AuctionBidHistoryResponseDto bidHistory(Long auctionId,Long memberId,int page,int size){return auctionBidHistoryUseCase.bidHistory(auctionId,memberId,page,size);}

    @Transactional(readOnly = true)
    public List<Long> findCloseTargetIds(LocalDateTime now,int batchSize){return auctionCloseUseCase.findCloseTargetIds(now,batchSize);}

    @Transactional
    public AuctionCloseResult closeAuction(Long auctionId,LocalDateTime now){return auctionCloseUseCase.closeAuction(auctionId,now);}
    // 입찰 없는 경매 일괄 유찰 (한 트랜잭션)
    @Transactional
    public int failNoBidAuctions(LocalDateTime now,int batchSize){return auctionCloseUseCase.failNoBidAuctions(now,batchSize);}

    @Transactional
    public AuctionDetailResponseDto detail(Long auctionId,Long memberId){return auctionDetailUseCase.detail(auctionId,memberId,LocalDateTime.now());}

    @Transactional
    public void applyPaymentSucceeded(Long auctionId) {auctionTradeResultUseCase.applyPaymentSucceeded(auctionId);}

    @Transactional
    public void applyPaymentDefaulted(Long auctionId) {auctionTradeResultUseCase.applyPaymentDefaulted(auctionId, LocalDateTime.now());}

    @Transactional
    public void applyPurchaseConfirmed(Long auctionId) {auctionTradeResultUseCase.applyPurchaseConfirmed(auctionId);}

    @Transactional
    public void applyRefundAccepted(Long auctionId) {auctionTradeResultUseCase.applyRefundAccepted(auctionId, LocalDateTime.now());}

    @Transactional(readOnly = true)
    public List<AuctionMyBidDto> myBids(Long memberId){return auctionMyBidsUseCase.myBids(memberId, LocalDateTime.now());}

    @Transactional(readOnly = true)
    public EnglishAuctionListResponseDto englishList(AuctionListStatus status, String category, AuctionSortType sort, int page, int size){return auctionListUseCase.englishList(status, category, sort, page, size, LocalDateTime.now());}

    @Transactional
    public DutchBuyResponseDto dutchBuy(Long auctionId, Long memberId, Long expectedPrice) {return auctionDutchBuyUseCase.buy(auctionId, memberId, expectedPrice, LocalDateTime.now());}

    @Transactional(readOnly = true)
    public DutchAuctionListResponseDto dutchList(AuctionListStatus status, int page, int size) {return auctionListUseCase.dutchList(status, page, size, LocalDateTime.now());}

    @Transactional(readOnly = true)
    public List<Long> findEndingSoonTargetIds(LocalDateTime now, int batchSize) {return auctionEndingSoonUseCase.findTargetIds(now, batchSize);}

    @Transactional
    public boolean notifyEndingSoon(Long auctionId, LocalDateTime now) {return auctionEndingSoonUseCase.notifyEndingSoon(auctionId, now);}
}

