package com.jjinmak.back.boundedContext.auction.app;

import com.jjinmak.back.boundedContext.auction.domain.Auction;
import com.jjinmak.back.boundedContext.auction.domain.AuctionStatus;
import com.jjinmak.back.boundedContext.auction.dto.AuctionCreateDto;
import com.jjinmak.back.boundedContext.auction.out.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuctionCreateUseCase {
    private final AuctionRepository auctionRepository;

    public void createAuction(AuctionCreateDto dto){
        if (auctionRepository.existsByProductIdAndStatus(dto.productId(), AuctionStatus.IN_PROGRESS)) {
            return;
        }
        Auction auction = Auction.create(
                dto.productId(),
                dto.sellerId(),
                dto.startPrice(),
                dto.instantWinPrice(),
                dto.shippingFee(),
                dto.endAt(),
                LocalDateTime.now()
        );
        auctionRepository.save(auction);
    }
}
