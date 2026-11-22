package com.jjinmak.boundedcontext.market.in;

import com.jjinmak.boundedcontext.market.app.MarketFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketAuctionOpenScheduler {

    // 한 트랜잭션에서 여는 상품 수. 아웃박스 전송 묶음(100)과 맞췄다.
    static final int BATCH_SIZE = 100;

    private final MarketFacade marketFacade;

    // 1초마다 시작 예정 시각이 된 상품의 경매를 연다. (#516: 60초 → 1초, 시작 지연 0~60초 → 0~1초)
    // 대상 조회는 idx_product_auction_open(auction_requested_at, auction_scheduled_at)을 탄다(V8).
    // fixedDelay라 이전 실행이 끝나야 다음 실행이 시작되어 같은 상품을 겹쳐 처리하지 않는다.
    // #548: 상품마다 트랜잭션 → 100건씩 한 트랜잭션(일괄 열기). 묶음이 실패하면 그 묶음만 건별로 다시 처리해서
    // 문제 있는 상품 하나 때문에 나머지가 다음 주기로 밀리지 않게 한다.
    @Scheduled(fixedDelay = 1_000)
    public void openAuctions() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> targetIds = marketFacade.findAuctionOpenTargetIds(now);

        for (int from = 0; from < targetIds.size(); from += BATCH_SIZE) {
            List<Long> batch = targetIds.subList(from, Math.min(from + BATCH_SIZE, targetIds.size()));
            try {
                marketFacade.openAuctions(batch, now);
            } catch (Exception e) {
                log.warn("경매 일괄 열기 실패, 건별로 다시 처리 size={}", batch.size(), e);
                openOneByOne(batch, now);
            }
        }
    }

    private void openOneByOne(List<Long> productIds, LocalDateTime now) {
        for (Long productId : productIds) {
            try {
                marketFacade.openAuction(productId, now);
            } catch (Exception e) {
                // 상품의 요청 표시까지 롤백되어 다음 주기에 다시 시도된다.
                log.warn("경매 열기 실패 productId={}", productId, e);
            }
        }
    }
}
