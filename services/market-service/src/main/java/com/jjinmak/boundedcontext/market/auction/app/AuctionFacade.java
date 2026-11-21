package com.jjinmak.boundedcontext.market.auction.app;

import com.jjinmak.boundedcontext.market.auction.domain.*;
import com.jjinmak.boundedcontext.market.auction.dto.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.jjinmak.contracts.auction.event.AuctionBidSucceedEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    private final AuctionStreamUseCase auctionStreamUseCase;


    @Transactional
    public AuctionCreateResultDto createAuction(AuctionCreateDto dto){
        return auctionCreateUseCase.createAuction(dto);
    }

    // 일괄 열기 (#548): market 경매 시작 흐름의 트랜잭션에 참여한다.
    @Transactional
    public List<AuctionCreateResultDto> createAuctions(List<AuctionCreateDto> dtos){
        return auctionCreateUseCase.createAuctions(dtos);
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

    // 종료 타이머 미리 읽기 (#548 4차)
    @Transactional(readOnly = true)
    public List<AuctionDeadlineDto> findDeadlinesBefore(LocalDateTime until, int limit){return auctionCloseUseCase.findDeadlinesBefore(until, limit);}

    @Transactional
    public AuctionCloseResult closeAuction(Long auctionId,LocalDateTime now){return auctionCloseUseCase.closeAuction(auctionId,now);}
    // 입찰 없는 경매 일괄 유찰 (한 트랜잭션)
    @Transactional
    public int failNoBidAuctions(LocalDateTime now,int batchSize){return auctionCloseUseCase.failNoBidAuctions(now,batchSize);}
    // 입찰 있는 경매 일괄 낙찰 (한 트랜잭션, #548)
    @Transactional
    public int winBidAuctions(LocalDateTime now,int batchSize){return auctionCloseUseCase.winBidAuctions(now,batchSize);}

    // 실시간 구독 (SSE, #557). 트랜잭션을 열지 않는다: 연결이 열려 있는 동안 DB 커넥션을 잡지 않게
    public Optional<SseEmitter> subscribe(Long auctionId){return auctionStreamUseCase.subscribe(auctionId);}

    public void pushStreamAuction(Long auctionId){auctionStreamUseCase.pushAuction(auctionId);}

    public void pushStreamBid(AuctionBidSucceedEvent event){auctionStreamUseCase.pushBid(event);}

    public void broadcastStreamViewers(){auctionStreamUseCase.broadcastViewers();}

    public void streamHeartbeat(){auctionStreamUseCase.heartbeat();}

    // 트랜잭션을 열지 않는다 (#557): 캐시 적중이면 DB 커넥션을 아예 잡지 않는다.
    // 캐시에 없을 때만 리포지토리 조회(자체 읽기 전용 트랜잭션)로 커넥션을 잠깐 쓴다
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
