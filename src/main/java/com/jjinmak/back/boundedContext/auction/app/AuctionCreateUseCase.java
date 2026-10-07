package com.jjinmak.back.boundedContext.auction.app;

import com.jjinmak.back.boundedContext.auction.domain.Auction;
import com.jjinmak.back.boundedContext.auction.domain.AuctionStatus;
import com.jjinmak.back.boundedContext.auction.dto.AuctionCreateDto;
import com.jjinmak.back.boundedContext.auction.out.AuctionMemberRepository;
import com.jjinmak.back.boundedContext.auction.out.AuctionRepository;
import com.jjinmak.back.global.exception.BadRequestException;
import com.jjinmak.back.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuctionCreateUseCase {
    //판매자확인 -> 최근경매조회 -> 생성가능여부 판단-> 회차계산
    private final AuctionRepository auctionRepository;
    private final AuctionMemberRepository auctionMemberRepository;

    public Long createAuction(AuctionCreateDto dto){
        if(!auctionMemberRepository.existsByUuid(dto.sellerId())){
            throw new NotFoundException("AUCTION001","존재하지 않는 회원입니다.");
        }
        Optional<Auction> lastAuction = auctionRepository.findTopByProductIdOrderByRoundDesc(dto.productId());
        if(lastAuction.isPresent() && lastAuction.get().getStatus() != AuctionStatus.FAILED){
            throw new BadRequestException("AUCTION002","이미 진행 중이거나 완료된 경매가 있는 상품입니다.");
        }
        int round = lastAuction.map(auction->auction.getRound()+1).orElse(1);

        Auction auction = Auction.create(
                dto.productId(),
                dto.sellerId(),
                round,
                dto.startPrice(),
                dto.instantWinPrice(),
                dto.duration(),
                LocalDateTime.now()
        );
        return auctionRepository.save(auction).getId();
    }
}
