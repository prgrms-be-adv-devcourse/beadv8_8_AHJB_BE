package com.jjinmak.back.boundedContext.refund.in;

import com.jjinmak.back.boundedContext.refund.app.RefundFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRetryScheduler {

    // ACCEPTED가 된 뒤 이 시간이 지나도 COMPLETED가 아니면 지갑 환불 요청이 유실된 것으로 본다.
    private static final Duration GRACE_PERIOD = Duration.ofMinutes(10);

    private final RefundFacade refundFacade;

    // 10분마다 실행. 지갑 리스너 실패·서버 중단으로 ACCEPTED에 머문 환불을 지갑에 다시 요청한다.
    @Scheduled(fixedDelay = 600_000)
    public void retryAcceptedRefunds() {
        LocalDateTime before = LocalDateTime.now().minus(GRACE_PERIOD);
        List<Long> ids = refundFacade.findRetryTargetIds(before);
        if (ids.isEmpty()) {
            return;
        }

        int requested = 0, skipped = 0, error = 0;
        for (Long id : ids) {
            try {
                switch (refundFacade.retryRefund(id)) {
                    case REQUESTED -> requested++;
                    case SKIPPED -> skipped++;
                }
            } catch (Exception e) {
                error++;
                log.warn("환불 재처리 요청 실패 refundId={}", id, e);
            }
        }
        log.info("환불 재처리 요청 대상={}, 요청={}, 건너뜀={}, 실패={}",
                ids.size(), requested, skipped, error);
    }
}