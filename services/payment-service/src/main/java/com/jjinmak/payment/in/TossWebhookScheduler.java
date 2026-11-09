package com.jjinmak.payment.in;

import com.jjinmak.payment.app.facade.TossWebhookFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 토스 웹훅 처리 스케줄러.
 *
 * 저장된 웹훅(RECEIVED / FAILED 중 재시도 한도 미만)을 꺼내 1건씩 처리한다. 건마다 별도 트랜잭션이다.
 * 처리 중 예외가 나면 그 건의 트랜잭션은 롤백되므로, 실패 기록은 별도 트랜잭션(recordFailure)으로 남기고 다음 건으로 넘어간다.
 *
 * 승인 진행 중인 충전은 처리 유스케이스가 5분 동안 건너뛰므로, 1분 간격이면 승인 누락은 대략 5~6분 안에 복구된다.
 *
 * TODO: 서버를 늘리면 모든 JVM 에서 돌아감 (PaymentScheduler 와 같은 문제. 분산 락 도입 시 함께 처리)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossWebhookScheduler {

    private final TossWebhookFacade tossWebhookFacade;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void processWebhooks() {
        for (Long eventId : tossWebhookFacade.findProcessableIds()) {
            try {
                tossWebhookFacade.process(eventId);
            } catch (Exception e) {
                log.warn("[토스 웹훅] 처리 실패, 다음 주기에 재시도. eventId={}", eventId, e);
                recordFailure(eventId, e);
            }
        }
    }

    private void recordFailure(Long eventId, Exception cause) {
        try {
            tossWebhookFacade.recordFailure(eventId, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (Exception e) {
            log.error("[토스 웹훅] 실패 기록도 실패. eventId={}", eventId, e);
        }
    }
}
