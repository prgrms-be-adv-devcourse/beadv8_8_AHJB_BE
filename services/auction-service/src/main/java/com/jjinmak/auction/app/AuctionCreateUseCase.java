package com.jjinmak.auction.app;

import com.jjinmak.auction.domain.EnglishAuction;
import com.jjinmak.auction.dto.AuctionCreateDto;
import com.jjinmak.auction.out.AuctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionCreateUseCase {
    private final AuctionRepository auctionRepository;
    private final AuctionEventPublisher auctionEventPublisher;

    public void createAuction(AuctionCreateDto dto){
        if (auctionRepository.existsByProductId(dto.productId())) {
            log.info("경매 생성 중복 수신, 무시: productId={}", dto.productId());
            return;
        }
        EnglishAuction auction = EnglishAuction.create(
                dto.productId(),
                dto.sellerId(),
                dto.startPrice(),
                dto.instantWinPrice(),
                dto.shippingFee(),
                dto.category(),
                dto.endAt(),
                LocalDateTime.now()
        );
        EnglishAuction saved = auctionRepository.save(auction);
        auctionEventPublisher.publishStarted(saved);
    }
}
