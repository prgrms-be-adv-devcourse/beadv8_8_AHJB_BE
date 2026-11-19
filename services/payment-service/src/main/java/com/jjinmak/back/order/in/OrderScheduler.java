package com.jjinmak.back.order.in;

import com.jjinmak.back.order.domain.OrderOutbox;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.order.app.OrderFacade;
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
    public void remindPayment(){
        List<Long> itemIds = orderFacade.findPaymentDueSoonItemIds();

        for (Long id : itemIds){
            try {
                orderFacade.sendPaymentDueSoonReminder(id);
            } catch (BusinessException e){
                log.error("오류가 발생해 결제 임박 알림을 전송하지 못했습니다.\ncartId: {}\nerrorCode: {}", id, e.getErrorCode());
            } catch (Exception e){
                log.error("결제 임박 알림 전송 실패.\ncartId: {}", id, e);
            }
        }
    }

    // TODO: 서버를 늘리면 모든 JVM에서 스케줄러가 돌아감
    @Scheduled(fixedDelay = 60_000)
    public void deleteDefaultPayment(){
        List<Long> itemIds = orderFacade.findExpiredItemIds();

        for (Long id : itemIds){
            try {
                orderFacade.deleteExpiredItem(id);
            } catch (BusinessException e){
                log.error("오류가 발생해 만료 상품을 취소하지 못했습니다.\ncartId: %d\nerrorCode: %s".formatted(id, e.getErrorCode()));
            } catch (Exception e){
                log.error("만료 상품 삭제 실패.\ncartId: %d".formatted(id), e);
            }
        }
    }

    // TODO: 서버를 늘리면 모든 JVM에서 스케줄러가 돌아감
    @Scheduled(fixedDelay = 60_000)
    public void remindAutoConfirm(){
        List<Long> orderIds = orderFacade.findExpiredSoonOrderIds();

        for (Long id : orderIds){
            try {
                orderFacade.sendAutoConfirmReminder(id);
            } catch (BusinessException e){
                log.error("오류가 발생해 구매 확정 알림을 전송하지 못했습니다.\norderId: {}\nerrorCode: {}", id, e.getErrorCode());
            } catch (Exception e){
                log.error("구매 확정 알림 전송 실패.\norderId: {}", id, e);
            }
        }
    }

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

    @Scheduled(fixedDelay = 60_000)
    public void refundNonShippedOrder(){
        List<Long> orderIds = orderFacade.getNonShippedOrderIds();

        for (Long id : orderIds){
            try{
                orderFacade.requestSystemRefund(id);
            } catch (BusinessException e){
                log.error("오류가 발생해 자동 환불을 진행하지 못했습니다.\norderId: {}\nerrorCode: {}", id, e.getErrorCode());
            } catch (Exception e){
                log.error("알 수 없는 오류가 발생해 자동 환불을 진행하지 못했습니다.\norderId: {}", id, e);
            }
        }
    }

    // TODO: 결제에 1시간이 넘게 걸려 주문이 제거되는 경우, 지갑도 이 사실을 알 필요가 있음.
    @Scheduled(fixedDelay = 1_800_000)
    public void deleteOverWaitingOrder(){
        List<Long> groupIds = orderFacade.findOverWaitingGroupIds();

        for (Long id : groupIds){
            try {
                orderFacade.deleteOverWaitingGroup(id);
            } catch (BusinessException e){
                log.error("오류가 발생해 만료된 주문서를 삭제하지 못했습니다.\norderGroupId: {}\nerrorCode: {}", id, e.getErrorCode());
            } catch (Exception e){
                log.error("만료된 주문서 삭제 실패.\norderGroupId: {}", id, e);
            }
        }
    }

    @Scheduled(fixedDelay = 1_000)
    public void sendOutbox(){
        for (OrderOutbox outbox : orderFacade.findUnpublishedOutboxes()){
            try {
                orderFacade.sendOutbox(outbox);
            } catch (Exception e){
                log.error("주문 이벤트 발행 실패. 다음 주기에 다시 보냅니다.\noutboxId: {}\ntopic: {}", outbox.getId(), outbox.getTopic(), e);
                return;
            }
            orderFacade.markOutboxPublished(outbox.getId());
        }
    }
}
