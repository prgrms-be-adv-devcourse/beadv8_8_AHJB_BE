package com.jjinmak.back.boundedContext.auction.app;

import com.jjinmak.back.boundedContext.auction.domain.AuctionMember;
import com.jjinmak.back.boundedContext.auction.dto.AuctionCreateDto;
import com.jjinmak.back.shared.member.dto.MemberDto;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuctionFacade {
    private final AuctionSyncMemberUseCase auctionSyncMemberUseCase;
    private final AuctionCreateUseCase auctionCreateUseCase;
    private final AuctionReAuctionUseCase auctionReAuctionUseCase;

    @Transactional
    public AuctionMember syncMember(MemberDto dto){
        return auctionSyncMemberUseCase.syncMember(dto);
    }

    @Transactional
    public Long createAuction(AuctionCreateDto dto){
        return auctionCreateUseCase.createAuction(dto);
    }

    @Transactional
    public Long reAuction(AuctionCreateDto dto) {
        return auctionReAuctionUseCase.reAuction(dto);
    }
}
