package com.jjinmak.auction.app;


import com.jjinmak.auction.domain.EnglishAuction;
import com.jjinmak.auction.dto.AuctionDetailResponseDto;
import com.jjinmak.auction.dto.AuctionInfoDto;
import com.jjinmak.auction.dto.AuctionViewerDto;
import com.jjinmak.auction.exception.AuctionErrorCode;
import com.jjinmak.auction.out.AuctionRepository;
import com.jjinmak.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuctionDetailUseCase {
    private final AuctionRepository auctionRepository;
    public AuctionDetailResponseDto detail(Long auctionId, Long memberId, LocalDateTime now){
        EnglishAuction auction = auctionRepository.findById(auctionId)
                .filter(EnglishAuction.class::isInstance)
                .map(EnglishAuction.class::cast)
                .orElseThrow(() -> new BusinessException(AuctionErrorCode.AUCTION_NOT_FOUND));
        return new AuctionDetailResponseDto(AuctionInfoDto.of(auction,now), AuctionViewerDto.of(auction,memberId),now);
    }
}
