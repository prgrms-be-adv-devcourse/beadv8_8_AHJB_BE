package com.jjinmak.back.boundedContext.auction.app;

import com.jjinmak.back.boundedContext.auction.domain.Auction;
import com.jjinmak.back.boundedContext.auction.domain.AuctionStatus;
import com.jjinmak.back.boundedContext.auction.dto.AuctionCreateDto;
import com.jjinmak.back.boundedContext.auction.out.AuctionRepository;
import com.jjinmak.back.global.exception.BadRequestException;
import com.jjinmak.back.global.exception.ForbiddenException;
import com.jjinmak.back.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuctionReAuctionUseCase {

    private final AuctionRepository auctionRepository;

    public Long reAuction(AuctionCreateDto dto) {
        Auction lastAuction = auctionRepository.findTopByProductIdOrderByRoundDesc(dto.productId())
                .orElseThrow(() -> new NotFoundException("AUCTION008", "경매 이력이 없는 상품입니다."));

        if (lastAuction.getStatus() != AuctionStatus.FAILED) {
            throw new BadRequestException("AUCTION002", "이미 진행 중이거나 완료된 경매가 있는 상품입니다.");
        }

        if (!lastAuction.getSellerId().equals(dto.sellerId())) {
            throw new ForbiddenException("AUCTION009", "해당 상품의 판매자만 재경매를 열 수 있습니다.");
        }

        Auction auction = Auction.create(
                dto.productId(),
                dto.sellerId(),
                lastAuction.getRound() + 1,
                dto.startPrice(),
                dto.instantWinPrice(),
                dto.duration(),
                LocalDateTime.now()
        );

        return auctionRepository.save(auction).getId();
    }
}