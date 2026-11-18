package com.jjinmak.back.boundedContext.auction.app;


import com.jjinmak.back.boundedContext.auction.domain.Auction;
import com.jjinmak.back.boundedContext.auction.dto.AuctionDetailResponseDto;
import com.jjinmak.back.boundedContext.auction.dto.AuctionInfoDto;
import com.jjinmak.back.boundedContext.auction.dto.AuctionViewerDto;
import com.jjinmak.back.boundedContext.auction.exception.AuctionErrorCode;
import com.jjinmak.back.boundedContext.auction.out.AuctionRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuctionDetailUseCase {
    private final AuctionRepository auctionRepository;
    public AuctionDetailResponseDto detail(Long auctionId, Long memberId, LocalDateTime now){
        Auction auction = auctionRepository.findById(auctionId).orElseThrow(()->new BusinessException(AuctionErrorCode.AUCTION_NOT_FOUND));
        return new AuctionDetailResponseDto(AuctionInfoDto.of(auction), AuctionViewerDto.of(auction,memberId),now);
    }
}
