package com.jjinmak.boundedcontext.market.in;

import com.jjinmak.boundedcontext.market.app.MarketFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketAuctionOpenScheduler {

    private final MarketFacade marketFacade;

    // 1초마다 시작 예정 시각이 된 상품의 경매를 연다. (#516: 60초 → 1초, 시작 지연 0~60초 → 0~1초)
    // 대상 조회는 idx_product_auction_open(auction_requested_at, auction_scheduled_at)을 탄다(V8).
    // fixedDelay라 이전 실행이 끝나야 다음 실행이 시작되어 같은 상품을 겹쳐 처리하지 않는다.
    @Scheduled(fixedDelay = 1_000)
    public void openAuctions() {
        LocalDateTime now = LocalDateTime.now();

        for (Long productId : marketFacade.findAuctionOpenTargetIds(now)) {
            try {
                marketFacade.openAuction(productId, now);
            } catch (Exception e) {
                // 상품의 요청 표시까지 롤백되어 다음 주기에 다시 시도된다.
                log.warn("경매 열기 실패 productId={}", productId, e);
            }
        }
    }
}
