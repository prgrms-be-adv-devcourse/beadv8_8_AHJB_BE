package com.jjinmak.back.boundedContext.settlement.in;

import com.jjinmak.back.boundedContext.settlement.app.SettlementFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
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
}