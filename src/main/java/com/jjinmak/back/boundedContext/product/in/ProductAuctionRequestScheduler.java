package com.jjinmak.back.boundedContext.product.in;

import com.jjinmak.back.boundedContext.product.app.ProductFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductAuctionRequestScheduler {

    private final ProductFacade productFacade;

    // 1분마다 수정 기간이 끝난 상품을 찾아 경매 생성을 요청한다.
    @Scheduled(fixedDelay = 60_000)
    public void requestAuctions(){
        LocalDateTime now = LocalDateTime.now();
        List<Long> productIds = productFacade.findAuctionRequestTargetIds(now);

        for (Long productId : productIds){
            try {
                productFacade.requestAuction(productId, now);
            } catch (Exception e) {
                // 실패한 상품은 요청 표시가 롤백되어 다음 주기에 다시 시도된다.
                log.warn("경매 요청 실패 productId={}", productId, e);
            }
        }
    }
}
