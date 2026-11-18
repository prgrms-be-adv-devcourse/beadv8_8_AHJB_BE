package com.jjinmak.boundedcontext.auction.app;

import com.jjinmak.boundedcontext.auction.domain.AuctionStatus;
import com.jjinmak.boundedcontext.auction.domain.EnglishAuction;
import com.jjinmak.boundedcontext.auction.dto.AuctionMyBidDto;
import com.jjinmak.boundedcontext.auction.out.AuctionRepository;
import com.jjinmak.contracts.product.dto.ProductSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuctionMyBidsUseCase {
    private final AuctionRepository auctionRepository;
    private final AuctionProductReader auctionProductReader;

    public List<AuctionMyBidDto> myBids(Long memberId, LocalDateTime now) {
        List<EnglishAuction> auctions = auctionRepository.findMyBidding(memberId, AuctionStatus.IN_PROGRESS);
        Map<Long, ProductSummaryDto> products = auctionProductReader.getSummaries(auctions);

        return auctions.stream()
                .map(auction -> AuctionMyBidDto.of(auction, products.get(auction.getProductId()), memberId, now))
                .toList();
    }
}
