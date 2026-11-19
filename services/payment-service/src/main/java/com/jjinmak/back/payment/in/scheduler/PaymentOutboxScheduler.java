package com.jjinmak.back.payment.in.scheduler;

import com.jjinmak.back.payment.app.facade.PaymentOutboxFacade;
import com.jjinmak.back.payment.domain.PaymentOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결제 아웃박스 전송 스케줄러.
 *
 * 1초마다 아직 보내지 않은 아웃박스를 저장된 순서대로 Kafka 로 보내고, 브로커 응답을 받은 건만 발행으로 표시한다.
 * 한 건이 실패하면 그 회차를 멈춘다. 뒤의 건을 먼저 보내면 같은 키의 이벤트 순서가 바뀔 수 있기 때문이다.
 * 실패한 건은 다음 주기에 다시 보낸다.
 *
 * TODO: 서버를 늘리면 모든 JVM 에서 돌아감 (PaymentScheduler 와 같은 문제. 같은 이벤트가 중복 전송된다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOutboxScheduler {

    private final PaymentOutboxFacade paymentOutboxFacade;

    @Scheduled(fixedDelay = 1_000)
    public void sendOutbox() {
        for (PaymentOutbox outbox : paymentOutboxFacade.findUnpublished()) {
            try {
                paymentOutboxFacade.send(outbox);
            } catch (Exception e) {
                log.error("결제 이벤트 발행 실패, 다음 주기에 재시도. outboxId={} topic={}", outbox.getId(), outbox.getTopic(), e);
                return;
            }
            paymentOutboxFacade.markPublished(outbox.getId());
        }
    }
}
