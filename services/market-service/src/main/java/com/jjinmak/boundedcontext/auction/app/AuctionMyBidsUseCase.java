package com.jjinmak.boundedcontext.auction.app;

import com.jjinmak.boundedcontext.auction.domain.AuctionStatus;
import com.jjinmak.boundedcontext.auction.dto.AuctionMyBidDto;
import com.jjinmak.boundedcontext.auction.out.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuctionMyBidsUseCase {
    private final AuctionRepository auctionRepository;

    public List<AuctionMyBidDto> myBids(Long memberId, LocalDateTime now) {
        return auctionRepository.findMyBidding(memberId, AuctionStatus.IN_PROGRESS).stream()
                .map(auction -> AuctionMyBidDto.of(auction, memberId, now))
                .toList();
    }
}