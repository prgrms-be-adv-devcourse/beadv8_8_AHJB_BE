package com.jjinmak.auction.app;


import com.jjinmak.auction.domain.DutchAuction;
import com.jjinmak.auction.domain.EnglishAuction;
import com.jjinmak.auction.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuctionDetailUseCase {
    private final AuctionReader auctionReader;

    public AuctionDetailResponseDto detail(Long auctionId, Long memberId, LocalDateTime now) {
        return switch (auctionReader.get(auctionId)) {
            case EnglishAuction english -> EnglishAuctionDetailResponseDto.of(english, memberId, now);
            case DutchAuction dutch -> DutchAuctionDetailResponseDto.of(dutch, memberId, now);
            default -> throw new IllegalStateException("지원하지 않는 경매 유형입니다. auctionId=" + auctionId);
        };
    }
}
