package com.jjinmak.boundedcontext.market.app;

import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import com.jjinmak.boundedcontext.market.auction.dto.AuctionCreateResultDto;
import com.jjinmak.boundedcontext.market.in.dto.MarketRelistResponseDto;
import com.jjinmak.boundedcontext.market.product.app.ProductFacade;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductRelistDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

// 판매자가 유찰된 상품을 다음 회차 경매로 다시 연다. (바로 시작)
// 상품과 경매를 함께 바꾸는 흐름이라 market에 두고, 각 컨텍스트의 Facade만 호출한다.
@Service
@RequiredArgsConstructor
public class MarketRelistUseCase {

    private final ProductFacade productFacade;
    private final AuctionFacade auctionFacade;

    // 한 트랜잭션에서 처리한다. 어느 단계든 실패하면 모두 롤백된다.
    // 1) 상품: 본인·방식 확인, 회차 +1, 새 회차 판매 조건 확정
    // 2) 경매: 이전 회차가 다시 열 수 있는 상태인지 확인 (유찰·미결제 무효)
    // 3) 경매: 새 회차 생성  4) 상품: 경매 id 기록
    public MarketRelistResponseDto relist(Long sellerId, Long productId, LocalDateTime now) {
        ProductRelistDto relist = productFacade.relist(sellerId, productId, now);
        auctionFacade.validateRelistable(relist.previousAuctionId());

        AuctionCreateResultDto created = auctionFacade.createAuction(MarketAuctionRequests.from(relist.open()));
        productFacade.startAuction(productId, created.auctionId());

        return new MarketRelistResponseDto(created.auctionId(), relist.open().round());
    }
}
