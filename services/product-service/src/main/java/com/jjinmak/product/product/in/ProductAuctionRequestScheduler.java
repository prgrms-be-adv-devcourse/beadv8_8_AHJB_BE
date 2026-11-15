package com.jjinmak.product.product.in;

import com.jjinmak.product.product.app.ProductFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductAuctionRequestScheduler {

    private final ProductFacade productFacade;

    // 1분마다
    // 1. 수정 기간이 끝난 상품을 찾아 경매 생성을 요청한다.
    // 2. 요청했는데 경매가 시작되지 않은 상품의 요청을 다시 보낸다. (요청 이벤트 유실 대비)
    @Scheduled(fixedDelay = 60_000)
    public void requestAuctions(){
        LocalDateTime now = LocalDateTime.now();

        for (Long productId : productFacade.findAuctionRequestTargetIds(now)){
            try {
                productFacade.requestAuction(productId, now);
            } catch (Exception e) {
                // 요청 표시가 롤백되어 다음 주기에 다시 시도된다.
                log.warn("경매 요청 실패 productId={}", productId, e);
            }
        }

        for (Long productId : productFacade.findAuctionResendTargetIds(now)){
            try {
                productFacade.resendAuction(productId);
            } catch (Exception e) {
                // 다음 주기에 다시 시도된다.
                log.warn("경매 요청 재전송 실패 productId={}", productId, e);
            }
        }
    }
}
