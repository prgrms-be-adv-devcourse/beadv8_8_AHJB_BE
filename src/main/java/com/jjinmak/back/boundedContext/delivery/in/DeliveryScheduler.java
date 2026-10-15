package com.jjinmak.back.boundedContext.delivery.in;

import com.jjinmak.back.boundedContext.delivery.app.DeliveryFacade;
import com.jjinmak.back.boundedContext.delivery.app.dto.WebhookTargetDto;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryScheduler {

    private final DeliveryFacade deliveryFacade;

    @Async
    @Scheduled(fixedDelay = 86_400_000)
    public void updateWebhook(){
        List<WebhookTargetDto> deliveries = deliveryFacade.getUnshippedDelivery();

        for (WebhookTargetDto delivery : deliveries){
            DeliveryStatusCode latestStatus = null;
            try {
                latestStatus = deliveryFacade.getDeliveryStatus(delivery.deliveryId());
            } catch (BusinessException e){
                log.error("배송 상태 동기화에 실패했습니다.\ndeliveryId: %d\nerrorCode: %s".formatted(delivery.deliveryId(), e.getErrorCode()));
            } catch (Exception e){
                log.error("알수 없는 오류로 배송 상태 동기화에 실패했습니다.\ndeliveryId: %d".formatted(delivery.deliveryId()), e);
            }

            try {
                deliveryFacade.updateWebhook(delivery, latestStatus);
            } catch (BusinessException e){
                log.error("Webhook 재등록에 실패했습니다.\ndeliveryId: %d\nerrorCode: %s".formatted(delivery.deliveryId(), e.getErrorCode()));
            } catch (Exception e){
                log. error("알수 없는 오류로 재등록에 실패했습니다.\ndeliveryId: %d".formatted(delivery.deliveryId()), e);
            }
        }
    }
}
