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

    // 1분마다 시작 예정 시각이 된 상품의 경매를 연다.
    @Scheduled(fixedDelay = 60_000)
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
