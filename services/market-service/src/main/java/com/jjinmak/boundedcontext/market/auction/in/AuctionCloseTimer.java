package com.jjinmak.boundedcontext.market.auction.in;

import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import com.jjinmak.boundedcontext.market.auction.dto.AuctionDeadlineDto;
import com.jjinmak.boundedcontext.market.shared.timer.TimingWheel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.jjinmak.boundedcontext.market.auction.in.AuctionCloseRunner.Trigger.TIMER;

/**
 * 경매 종료 타이머 (#548 4차): 종료 시각에 맞춰 종료 처리를 깨운다.
 *
 * <p>3차까지 처리 시간은 약 0.2초로 줄었고, 남은 지연은 "1초 주기 폴링이 깨어날 때까지 기다리는 시간(0~1초)"이었다.
 * 최악이면 1.2초로 낙찰 목표(1초)를 넘는다 → 폴링 대신 종료 시각에 깨운다. 처리 내용(일괄 낙찰·유찰, 아웃박스)은 그대로다.
 *
 * <ul>
 *   <li>미리 읽기(5초마다): PRELOAD_WINDOW 안에 끝나는 진행 중 경매만 휠에 등록. 이미 등록된 같은 시각은 건너뛴다</li>
 *   <li>tick(100ms마다): 종료 시각이 지난 경매가 하나라도 있으면 종료 처리를 한 번 부른다(경매 1건마다가 아니라 칸마다 1번)</li>
 *   <li>연장(soft close): 다시 예약하지 않는다. 옛 시각에 깨어나도 종료 처리 쿼리가 end_at <= now로 걸러 건드리지 않고,
 *       새 시각은 다음 미리 읽기에서 등록된다(연장은 분 단위라 윈도 10초 안에 다시 들어온다)</li>
 *   <li>재시작: 휠은 비어 시작하지만 첫 미리 읽기(바로 실행)가 채운다. 그 사이는 안전망 폴링이 잡는다</li>
 *   <li>서버 여러 대: 여러 대의 타이머가 함께 깨어나도 일괄 처리의 FOR UPDATE SKIP LOCKED·상태 조건으로 중복 처리 없음</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "market.auction.close.timer.enabled", havingValue = "true", matchIfMissing = true)
public class AuctionCloseTimer {

    static final long TICK_MS = 100;
    static final int WHEEL_SIZE = 512;                              // 512 × 100ms = 51.2초 한 바퀴 (윈도보다 넉넉히)
    static final Duration PRELOAD_WINDOW = Duration.ofSeconds(10);  // 미리 읽기 주기(5초)보다 길어야 빈틈이 없다
    static final int PRELOAD_LIMIT = 5_000;

    private final AuctionFacade auctionFacade;
    private final AuctionCloseRunner auctionCloseRunner;
    private final TimingWheel<Long> wheel = new TimingWheel<>(TICK_MS, WHEEL_SIZE, System.currentTimeMillis());

    public AuctionCloseTimer(AuctionFacade auctionFacade, AuctionCloseRunner auctionCloseRunner) {
        this.auctionFacade = auctionFacade;
        this.auctionCloseRunner = auctionCloseRunner;
    }

    @Scheduled(fixedDelay = 5_000)
    public void preload() {
        try {
            List<AuctionDeadlineDto> deadlines =
                    auctionFacade.findDeadlinesBefore(LocalDateTime.now().plus(PRELOAD_WINDOW), PRELOAD_LIMIT);
            for (AuctionDeadlineDto deadline : deadlines) {
                wheel.schedule(deadline.auctionId(), toEpochMillis(deadline.endAt()));
            }
        } catch (Exception e) {
            log.warn("경매 종료 타이머 미리 읽기 실패 (안전망 폴링이 대신 처리)", e);
        }
    }

    @Scheduled(fixedRate = TICK_MS)
    public void tick() {
        List<Long> due = wheel.advance(System.currentTimeMillis());
        if (!due.isEmpty()) {
            auctionCloseRunner.run(TIMER);
        }
    }

    private static long toEpochMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
