package com.jjinmak.back.boundedContext.auction.app;

import com.jjinmak.back.boundedContext.auction.domain.AuctionStatus;
import com.jjinmak.back.boundedContext.auction.dto.AuctionMyBidDto;
import com.jjinmak.back.boundedContext.auction.out.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuctionMyBidsUseCase {
    private final AuctionRepository auctionRepository;

    public List<AuctionMyBidDto> myBids(Long memberId) {
        return auctionRepository.findMyBidding(memberId, AuctionStatus.IN_PROGRESS).stream()
                .map(auction -> AuctionMyBidDto.of(auction, memberId))
                .toList();
    }
}