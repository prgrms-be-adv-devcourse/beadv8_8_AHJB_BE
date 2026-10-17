package com.jjinmak.back.boundedContext.order.in.cart;

import com.jjinmak.back.boundedContext.order.app.cart.CartFacade;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartScheduler {

    private final CartFacade cartFacade;

    // TODO: 서버를 늘리면 모든 JVM에서 스케줄러가 돌아감
    @Scheduled(fixedDelay = 60_000)
    public void deleteDefaultPayment(){
        List<Long> itemIds = cartFacade.findExpiredItemIds();

        for (Long id : itemIds){
            try {
                cartFacade.deleteExpiredItem(id);
            } catch (BusinessException e){
                log.error("오류가 발생해 만료 상품을 취소하지 못했습니다.\ncartId: %d\nerrorCode: %s".formatted(id, e.getErrorCode()));
            } catch (Exception e){
                log.error("만료 상품 삭제 실패.\ncartId: %d".formatted(id), e);
            }
        }
    }
}
