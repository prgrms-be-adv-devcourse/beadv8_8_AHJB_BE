package com.jjinmak.auction.app;

import com.jjinmak.auction.domain.AuctionMember;
import com.jjinmak.auction.out.AuctionMemberRepository;
import com.jjinmak.contracts.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class AuctionSyncMemberUseCase {
    private final AuctionMemberRepository auctionMemberRepository;

    public AuctionMember syncMember(MemberDto memberDto){
        Long id = memberDto.id();
        String nickname = memberDto.nickname();
        AuctionMember member = auctionMemberRepository.findById(id)
                .orElseGet(() -> new AuctionMember(id, nickname));

        member.sync(nickname);

        return auctionMemberRepository.save(member);
    }
}
