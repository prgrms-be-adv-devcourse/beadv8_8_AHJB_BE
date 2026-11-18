package com.jjinmak.boundedcontext.market.app;

import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import com.jjinmak.boundedcontext.market.auction.dto.AuctionCreateResultDto;
import com.jjinmak.boundedcontext.market.product.app.ProductFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// 시작 예정 시각이 된 상품의 경매를 연다.
// 상품과 경매를 함께 바꾸는 흐름이라 market에 두고, 각 컨텍스트의 Facade만 호출한다.
@Service
@RequiredArgsConstructor
public class MarketAuctionOpenUseCase {

    private final ProductFacade productFacade;
    private final AuctionFacade auctionFacade;

    public List<Long> findTargetIds(LocalDateTime now) {
        return productFacade.findAuctionRequestTargetIds(now);
    }

    // 한 트랜잭션에서 처리한다. 어느 단계든 실패하면 모두 롤백되고 다음 주기에 다시 시도된다.
    // 1) 상품: 요청 표시, 판매 조건 확정  2) 경매: 생성  3) 상품: 경매 id 기록
    public void openAuction(Long productId, LocalDateTime now) {
        productFacade.prepareAuctionOpen(productId, now).ifPresent(open -> {
            AuctionCreateResultDto created = auctionFacade.createAuction(MarketAuctionRequests.from(open));
            productFacade.startAuction(productId, created.auctionId());
        });
    }
}
