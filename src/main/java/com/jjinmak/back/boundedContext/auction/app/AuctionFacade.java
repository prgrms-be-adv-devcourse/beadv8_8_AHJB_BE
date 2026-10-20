package com.jjinmak.back.boundedContext.auction.app;

import com.jjinmak.back.boundedContext.auction.domain.AuctionCloseResult;
import com.jjinmak.back.boundedContext.auction.domain.AuctionMember;
import com.jjinmak.back.boundedContext.auction.dto.*;
import com.jjinmak.back.shared.member.dto.MemberDto;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuctionFacade {
    private final AuctionSyncMemberUseCase auctionSyncMemberUseCase;
    private final AuctionCreateUseCase auctionCreateUseCase;
    private final AuctionBidUseCase auctionBidUseCase;
    private final AuctionInstantWinUseCase auctionInstantWinUseCase;
    private final AuctionBidHistoryUseCase auctionBidHistoryUseCase;
    private final AuctionCloseUseCase auctionCloseUseCase;
    private final AuctionDetailUseCase auctionDetailUseCase;
    private final AuctionTradeResultUseCase auctionTradeResultUseCase;
    private final AuctionMyBidsUseCase auctionMyBidsUseCase;

    @Transactional
    public AuctionMember syncMember(MemberDto dto){
        return auctionSyncMemberUseCase.syncMember(dto);
    }

    @Transactional
    public void createAuction(AuctionCreateDto dto){
         auctionCreateUseCase.createAuction(dto);
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
    public List<AuctionMyBidDto> myBids(Long memberId){return auctionMyBidsUseCase.myBids(memberId);}
}
