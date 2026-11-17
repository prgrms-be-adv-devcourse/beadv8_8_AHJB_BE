package com.jjinmak.auction.app;

import com.jjinmak.auction.domain.AuctionStatus;
import com.jjinmak.auction.dto.AuctionMyBidDto;
import com.jjinmak.auction.out.AuctionRepository;
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