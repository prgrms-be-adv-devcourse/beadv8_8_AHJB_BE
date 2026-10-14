package com.jjinmak.back.boundedContext.settlement.in;

import com.jjinmak.back.boundedContext.settlement.app.SettlementFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementPayoutScheduler {

    private final SettlementFacade settlementFacade;

    // 매일 03:00 실행.
    // 지급일이 지난 PENDING 정산서까지 처리하므로 15일에 실패·누락된 건은 다음 날 다시 시도된다.
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void payout() {
        LocalDate today =  LocalDate.now();
        List<Long> ids = settlementFacade.findPayoutTargetIds(today);
        if (ids.isEmpty()) {
            return;
        }

        int completed = 0, skipped = 0, conflict = 0, error = 0;
        for (Long id : ids) {
            try {
                switch (settlementFacade.completeSettlement(id, today)) {
                    case COMPLETED -> completed++;
                    case SKIPPED -> skipped++;
                }
            } catch (OptimisticLockingFailureException e) {
                conflict++;
                log.info("정산 지급 충돌 settlementId={}", id);
            } catch (Exception e) {
                error++;
                log.warn("정산 지급 실패 settlementId={}", id, e);
            }
        }
        log.info("정산 지급 대상={}, 완료={}, 건너뜀={}, 충돌={}, 실패={}",
                ids.size(), completed, skipped, conflict, error);
    }
}
