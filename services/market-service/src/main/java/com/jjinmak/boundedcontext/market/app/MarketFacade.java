package com.jjinmak.boundedcontext.market.app;

import com.jjinmak.boundedcontext.market.in.dto.MarketRelistResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// 상품과 경매를 함께 다루는 흐름의 입구
@Service
@RequiredArgsConstructor
public class MarketFacade {

    private final MarketAuctionOpenUseCase marketAuctionOpenUseCase;
    private final MarketRelistUseCase marketRelistUseCase;

    @Transactional(readOnly = true)
    public List<Long> findAuctionOpenTargetIds(LocalDateTime now) {
        return marketAuctionOpenUseCase.findTargetIds(now);
    }

    // 상품마다 별도 트랜잭션으로 처리해서, 하나가 실패해도 다른 상품은 경매로 넘어간다.
    @Transactional
    public void openAuction(Long productId, LocalDateTime now) {
        marketAuctionOpenUseCase.openAuction(productId, now);
    }

    @Transactional
    public MarketRelistResponseDto relist(Long sellerId, Long productId, LocalDateTime now) {
        return marketRelistUseCase.relist(sellerId, productId, now);
    }
}
