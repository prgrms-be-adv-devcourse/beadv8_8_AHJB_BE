package com.jjinmak.boundedcontext.market.auction.in;

import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import com.jjinmak.boundedcontext.market.auction.domain.AuctionCloseResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 경매 종료 처리 한 번 (#548). "언제 깨울지"(타이머·안전망 폴링)와 "깨어나서 할 일"을 나눈다.
 *
 * <ol>
 *   <li>입찰 있는 영국식 경매는 UPDATE 한 번으로 일괄 낙찰. 거래(주문·결제)로 이어지므로 먼저 처리한다</li>
 *   <li>입찰 없는 경매(영국식 입찰 0건, 하향식)는 UPDATE 한 번으로 일괄 유찰 (#516)</li>
 *   <li>남은 대상(일괄 처리 중 다른 트랜잭션이 잡고 있어 건너뛴 경매, H2)은 경매 1건 → 트랜잭션 1개로 처리.
 *       일괄 처리가 실패해도 여기서 건별로 처리된다</li>
 * </ol>
 *
 * <p>타이머와 안전망 폴링이 동시에 부르면 한쪽만 실행한다(락). 실행 중에 들어온 요청은 버리지 않고,
 * 지금 실행이 끝난 뒤 한 번 더 실행한다 → 실행 도중 종료 시각이 된 경매를 다음 주기까지 미루지 않는다.
 */
@Slf4j
@Component
public class AuctionCloseRunner {

    public enum Trigger { TIMER, POLL }

    private static final int BATCH_SIZE = 1000;

    private final AuctionFacade auctionFacade;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean rerun = new AtomicBoolean();
    private final Map<Trigger, Counter> triggerCounters = new EnumMap<>(Trigger.class);

    public AuctionCloseRunner(AuctionFacade auctionFacade, MeterRegistry meterRegistry) {
        this.auctionFacade = auctionFacade;
        for (Trigger trigger : Trigger.values()) {
            triggerCounters.put(trigger, Counter.builder("auction.close.trigger")
                    .description("경매 종료 처리를 깨운 횟수 (타이머 / 안전망 폴링)")
                    .tag("source", trigger.name().toLowerCase())
                    .register(meterRegistry));
        }
    }

    public void run(Trigger trigger) {
        triggerCounters.get(trigger).increment();
        while (true) {
            if (!lock.tryLock()) {
                rerun.set(true);   // 실행 중인 쪽이 끝난 뒤 한 번 더 돈다
                return;
            }
            try {
                do {
                    rerun.set(false);
                    closeOnce(LocalDateTime.now());
                } while (rerun.get());
            } finally {
                lock.unlock();
            }
            if (!rerun.get()) {
                return;   // 락을 푸는 사이에 들어온 요청이 없으면 끝
            }
        }
    }

    private void closeOnce(LocalDateTime now) {
        int bulkWon = 0;
        try {
            bulkWon = auctionFacade.winBidAuctions(now, BATCH_SIZE);
        } catch (Exception e) {
            log.warn("입찰 있는 경매 일괄 낙찰 실패, 건별 처리로 진행", e);
        }
        int bulkFailed = 0;
        try {
            bulkFailed = auctionFacade.failNoBidAuctions(now, BATCH_SIZE);
        } catch (Exception e) {
            log.warn("입찰 없는 경매 일괄 유찰 실패, 건별 처리로 진행", e);
        }
        List<Long> ids = auctionFacade.findCloseTargetIds(now, BATCH_SIZE);
        if (ids.isEmpty()) {
            if (bulkFailed > 0 || bulkWon > 0) {
                log.info("경매 종료 처리 일괄낙찰={}, 일괄유찰={}", bulkWon, bulkFailed);
            }
            return;
        }
        int won = 0, failed = 0, skipped = 0, conflict = 0, error = 0;
        for (Long id : ids) {
            try {
                AuctionCloseResult result = auctionFacade.closeAuction(id, now);
                switch (result) {
                    case WON -> won++;
                    case FAILED -> failed++;
                    case SKIPPED -> skipped++;
                }
            } catch (ObjectOptimisticLockingFailureException e) {
                conflict++;
                log.info("경매 종료 충돌 auctionId={}", id);
            } catch (Exception e) {
                error++;
                log.warn("경매 종료 실패 auctionId={}", id, e);
            }
        }
        log.info("경매 종료 처리 일괄낙찰={}, 일괄유찰={}, 건별 대상={}, 낙찰={}, 유찰={}, 건너뜀={}, 충돌={}, 실패={}",
                bulkWon, bulkFailed, ids.size(), won, failed, skipped, conflict, error);
    }
}
