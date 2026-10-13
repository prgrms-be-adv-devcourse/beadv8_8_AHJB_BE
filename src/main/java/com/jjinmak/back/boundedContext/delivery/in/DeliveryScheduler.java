package com.jjinmak.back.boundedContext.delivery.in;

import com.jjinmak.back.boundedContext.delivery.app.DeliveryFacade;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryScheduler {

    private final DeliveryFacade deliveryFacade;

    @Scheduled(fixedDelay = 86_400_000)
    public void updateWebhook(){
        List<Long> deliveryIds = deliveryFacade.getUnshippedDeliveryIds();

        for (Long id : deliveryIds){
            try {
                deliveryFacade.updateWebhook(id);
            } catch (BusinessException e){
                log.error("Webhook 재등록에 실패했습니다.\ndeliveryId: %d\nerrorCode: %s".formatted(id, e.getErrorCode()));
            } catch (Exception e){
                log. error("알수 없는 오류로 재등록에 실패했습니다.\ndeliveryId: %d".formatted(id), e);
            }
        }
    }
}
