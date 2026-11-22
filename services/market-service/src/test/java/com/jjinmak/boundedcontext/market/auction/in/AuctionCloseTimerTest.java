package com.jjinmak.boundedcontext.market.auction.in;

import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import com.jjinmak.boundedcontext.market.auction.dto.AuctionDeadlineDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.jjinmak.boundedcontext.market.auction.in.AuctionCloseRunner.Trigger.TIMER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuctionCloseTimerTest {

    private final AuctionFacade auctionFacade = mock(AuctionFacade.class);
    private final AuctionCloseRunner runner = mock(AuctionCloseRunner.class);
    private final AuctionCloseTimer timer = new AuctionCloseTimer(auctionFacade, runner);

    @Test
    @DisplayName("미리 읽은 경매의 종료 시각이 지나면 tick에서 종료 처리를 한 번 부른다")
    void tickRunsCloseWhenDue() throws InterruptedException {
        LocalDateTime soon = LocalDateTime.now().plusNanos(150_000_000);   // 0.15초 뒤
        when(auctionFacade.findDeadlinesBefore(any(), anyInt()))
                .thenReturn(List.of(new AuctionDeadlineDto(1L, soon), new AuctionDeadlineDto(2L, soon)));
        timer.preload();

        timer.tick();
        verify(runner, never()).run(TIMER);   // 아직 종료 시각 전

        Thread.sleep(300);
        timer.tick();
        verify(runner, times(1)).run(TIMER);   // 같은 칸의 2건 → 1번

        timer.tick();
        verify(runner, times(1)).run(TIMER);   // 이미 꺼낸 경매로 다시 부르지 않음
    }

    @Test
    @DisplayName("미리 읽을 경매가 없으면 종료 처리를 부르지 않는다")
    void noDeadlinesNoRun() {
        when(auctionFacade.findDeadlinesBefore(any(), anyInt())).thenReturn(List.of());
        timer.preload();

        timer.tick();

        verify(runner, never()).run(any());
    }

    @Test
    @DisplayName("미리 읽기가 실패해도 예외를 밖으로 던지지 않는다 (안전망 폴링이 대신 처리)")
    void preloadFailureIsSwallowed() {
        when(auctionFacade.findDeadlinesBefore(any(), anyInt())).thenThrow(new IllegalStateException("db down"));

        timer.preload();
    }
}
