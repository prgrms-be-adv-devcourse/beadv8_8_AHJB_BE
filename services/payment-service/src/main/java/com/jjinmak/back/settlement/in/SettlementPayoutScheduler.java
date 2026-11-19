package com.jjinmak.back.settlement.in;

import com.jjinmak.back.settlement.app.SettlementFacade;
import com.jjinmak.back.settlement.domain.SettlementOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementPayoutScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SettlementFacade settlementFacade;

    // 매일 03:00 실행.
    // 지급일이 지난 PENDING 정산서까지 요청하므로 지갑에서 실패·누락된 건은 다음 날 다시 요청된다.
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void payout() {
        LocalDate today = LocalDate.now(KST);
        List<Long> ids = settlementFacade.findPayoutTargetIds(today);
        if (ids.isEmpty()) {
            return;
        }

        int requested = 0, skipped = 0, error = 0;
        for (Long id : ids) {
            try {
                switch (settlementFacade.requestPayout(id, today)) {
                    case REQUESTED -> requested++;
                    case SKIPPED -> skipped++;
                }
            } catch (Exception e) {
                error++;
                log.warn("정산 지급 요청 실패 settlementId={}", id, e);
            }
        }
        log.info("정산 지급 요청 대상={}, 요청={}, 건너뜀={}, 실패={}",
                ids.size(), requested, skipped, error);
    }

    // 1초마다 아웃박스의 미발행 행을 Kafka 로 보낸다. 실패하면 그 회차를 멈춰 순서를 지킨다
    @Scheduled(fixedDelay = 1_000)
    public void sendOutbox() {
        for (SettlementOutbox outbox : settlementFacade.findUnpublishedOutboxes()) {
            try {
                settlementFacade.sendOutbox(outbox);
            } catch (Exception e) {
                log.error("정산 이벤트 발행 실패. 다음 주기에 다시 보냅니다. outboxId={}, topic={}", outbox.getId(), outbox.getTopic(), e);
                return;
            }
            settlementFacade.markOutboxPublished(outbox.getId());
        }
    }
}
