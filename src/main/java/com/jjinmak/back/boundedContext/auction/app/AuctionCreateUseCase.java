package com.jjinmak.back.boundedContext.auction.app;

import com.jjinmak.back.boundedContext.auction.domain.Auction;
import com.jjinmak.back.boundedContext.auction.dto.AuctionCreateDto;
import com.jjinmak.back.boundedContext.auction.out.AuctionMemberRepository;
import com.jjinmak.back.boundedContext.auction.out.AuctionRepository;
import com.jjinmak.back.global.exception.BadRequestException;
import com.jjinmak.back.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuctionCreateUseCase {
    //1회차경매생성
    private static final int FIRST_ROUND = 1;
    private final AuctionRepository auctionRepository;
    private final AuctionMemberRepository auctionMemberRepository;

    public Long createAuction(AuctionCreateDto dto){
        if(!auctionMemberRepository.existsByUuid(dto.sellerId())){
            throw new NotFoundException("AUCTION001","존재하지 않는 회원입니다.");
        }
        if (auctionRepository.existsByProductId(dto.productId())) {
            throw new BadRequestException("AUCTION002", "이미 경매가 등록된 상품입니다.");
        }
        Auction auction = Auction.create(
                dto.productId(),
                dto.sellerId(),
                FIRST_ROUND,
                dto.startPrice(),
                dto.instantWinPrice(),
                dto.duration(),
                LocalDateTime.now()
        );
        return auctionRepository.save(auction).getId();
    }
}
