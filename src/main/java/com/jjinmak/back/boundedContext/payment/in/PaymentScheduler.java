package com.jjinmak.back.boundedContext.payment.in;

import com.jjinmak.back.boundedContext.payment.app.PaymentFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결제 복구 스케줄러.
 *  - REQUESTED 에 40분 넘게 머문 충전: 토스에 조회해 반영하거나 닫는다 (건마다 별도 트랜잭션)
 *  - CHARGED 인데 USED 가 안 된 주문 충전: 발생하면 안 되는 상태라 로그만 남긴다
 *
 * TODO: 서버를 늘리면 모든 JVM 에서 돌아감 (CartScheduler 와 같은 문제)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentScheduler {

    private final PaymentFacade paymentFacade;

    @Scheduled(fixedDelay = 10 * 60_000, initialDelay = 60_000)
    public void recoverStaleCharges() {
        for (Long chargeId : paymentFacade.findStaleRequestedChargeIds()) {
            try {
                paymentFacade.recoverStaleCharge(chargeId);
            } catch (Exception e) {
                log.error("충전 복구 실패, 다음 주기에 재시도. chargeId={}", chargeId, e);
            }
        }

        for (Long chargeId : paymentFacade.findChargedButUnusedChargeIds()) {
            log.error("주문 충전이 CHARGED 에 머물러 있음 (홀딩 미완료). chargeId={}", chargeId);
        }
    }
}
