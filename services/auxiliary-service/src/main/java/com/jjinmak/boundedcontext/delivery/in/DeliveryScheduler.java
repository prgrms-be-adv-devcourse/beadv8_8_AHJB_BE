package com.jjinmak.boundedcontext.delivery.in;

import com.jjinmak.boundedcontext.delivery.domain.DeliveryOutbox;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.delivery.domain.DeliveryStatusCode;
import com.jjinmak.boundedcontext.delivery.app.DeliveryFacade;
import com.jjinmak.boundedcontext.delivery.app.dto.WebhookTargetDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.jjinmak.boundedcontext.delivery.config.DeliveryAsyncConfig.DELIVERY_TASK_EXECUTOR;
import static com.jjinmak.boundedcontext.delivery.exception.DeliveryErrorCode.DELIVERY_NON_REGISTERED_WAYBILL;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryScheduler {

    private final DeliveryFacade deliveryFacade;

    @Async(DELIVERY_TASK_EXECUTOR)
    @Scheduled(fixedDelay = 86_400_000)
    public void updateWebhook(){
        List<WebhookTargetDto> deliveries = deliveryFacade.getUnshippedDelivery();

        for (WebhookTargetDto delivery : deliveries){
            DeliveryStatusCode latestStatus = null;
            try {
                latestStatus = deliveryFacade.getDeliveryStatus(delivery.deliveryId());
            } catch (BusinessException e){
                if (e.getErrorCode().equals(DELIVERY_NON_REGISTERED_WAYBILL))
                    log.warn("택배사에 등록되지 않은 운송장입니다.\ndeliveryId: %d".formatted(delivery.deliveryId()));
                else
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

    @Scheduled(fixedDelay = 1_000)
    public void sendOutbox(){
        for (DeliveryOutbox outbox : deliveryFacade.findUnpublishedOutboxes()){
            try {
                deliveryFacade.sendOutbox(outbox);
            } catch (Exception e){
                log.error("Delivery 이벤트 발행 실패, 다음 주기에 다시 보냅니다.\noutboxId: {}\ntopic: {}", outbox.getId(), outbox.getTopic(), e);
                return;
            }
            deliveryFacade.markOutboxPublished(outbox.getId());
        }
    }
}
