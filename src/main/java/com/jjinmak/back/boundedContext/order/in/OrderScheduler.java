package com.jjinmak.back.boundedContext.order.in;

import com.jjinmak.back.boundedContext.order.app.OrderFacade;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private final OrderFacade orderFacade;

    // TODO: 서버를 늘리면 모든 JVM에서 스케줄러가 돌아감
    @Scheduled(fixedDelay = 60_000)
    public void confirmPurchaseAuto(){
        List<Long> orderIds = orderFacade.getExpiredOrderIds();

        for (Long id : orderIds){
            try {
                orderFacade.confirmPurchaseAuto(id);
            } catch (BusinessException e){
                log.error("오류가 발생해 자동 확정을 진행하지 못했습니다.\norderId: %d\nerrorCode: %s".formatted(id, e.getErrorCode()));
            } catch (Exception e){
                log.error("자동 구매 확정 실패.\norderId: %d".formatted(id), e);
            }
        }
    }
}
