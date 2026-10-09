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

    @Transactional
    public AuctionMember syncMember(MemberDto dto){
        return auctionSyncMemberUseCase.syncMember(dto);
    }

    @Transactional
    public void createAuction(AuctionCreateDto dto){
         auctionCreateUseCase.createAuction(dto);
    }

}
