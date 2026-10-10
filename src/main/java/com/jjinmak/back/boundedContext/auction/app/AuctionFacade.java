package com.jjinmak.back.boundedContext.auction.app;

import com.jjinmak.back.boundedContext.auction.domain.AuctionMember;
import com.jjinmak.back.boundedContext.auction.dto.AuctionBidResponseDto;
import com.jjinmak.back.boundedContext.auction.dto.AuctionCreateDto;
import com.jjinmak.back.boundedContext.auction.dto.AuctionInstantWinResponseDto;
import com.jjinmak.back.shared.member.dto.MemberDto;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuctionFacade {
    private final AuctionSyncMemberUseCase auctionSyncMemberUseCase;
    private final AuctionCreateUseCase auctionCreateUseCase;
    private final AuctionBidUseCase auctionBidUseCase;
    private final AuctionInstantWinUseCase auctionInstantWinUseCase;
    @Transactional
    public AuctionMember syncMember(MemberDto dto){
        return auctionSyncMemberUseCase.syncMember(dto);
    }

    @Transactional
    public void createAuction(AuctionCreateDto dto){
         auctionCreateUseCase.createAuction(dto);
    }

    @Transactional
    public AuctionBidResponseDto bid(Long auctionId, Long bidderId, Long bidPrice){
        return auctionBidUseCase.bid(auctionId,bidderId,bidPrice);
    }

    @Transactional
    public AuctionInstantWinResponseDto instantWin(Long auctionId,Long bidderId){
        return auctionInstantWinUseCase.instantWin(auctionId,bidderId);
    }

}
