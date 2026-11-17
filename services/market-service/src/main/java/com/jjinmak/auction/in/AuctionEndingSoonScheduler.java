package com.jjinmak.auction.in;

import com.jjinmak.auction.app.AuctionFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEndingSoonScheduler {
    private final AuctionFacade auctionFacade;

    // 10초마다 종료 10분 이내 경매를 찾아 경매당 1회 발행한다
    @Scheduled(fixedDelay = 10_000)
    public void publishEndingSoon() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> ids = auctionFacade.findEndingSoonTargetIds(now, 1000);
        if (ids.isEmpty()) { return; }
        int published = 0, skipped = 0, error = 0;
        for (Long id : ids) {
            try {
                if (auctionFacade.notifyEndingSoon(id, now)) { published++; } else { skipped++; }
            } catch (Exception e) {
                error++;
                log.warn("마감 임박 발행 실패 auctionId={}", id, e);
            }
        }
        log.info("마감 임박 대상={}, 발행={}, 건너뜀={}, 실패={}", ids.size(), published, skipped, error);
    }
}