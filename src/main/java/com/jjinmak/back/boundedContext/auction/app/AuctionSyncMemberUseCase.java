package com.jjinmak.back.boundedContext.auction.app;

import com.jjinmak.back.boundedContext.auction.domain.AuctionMember;
import com.jjinmak.back.boundedContext.auction.out.AuctionMemberRepository;
import com.jjinmak.back.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuctionSyncMemberUseCase {
    private final AuctionMemberRepository auctionMemberRepository;

    public AuctionMember syncMember(MemberDto memberDto){
        UUID uuid = memberDto.uuid();
        String nickname = memberDto.nickname();
        AuctionMember member = auctionMemberRepository.findByUuid(uuid)
                .orElseGet(() -> new AuctionMember(uuid, nickname));

        member.sync(nickname);

        return auctionMemberRepository.save(member);
    }
}
