package com.jjinmak.boundedcontext.market.auction.in;

import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import com.jjinmak.boundedcontext.market.auction.domain.AuctionCloseResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.jjinmak.boundedcontext.market.auction.in.AuctionCloseRunner.Trigger.POLL;
import static com.jjinmak.boundedcontext.market.auction.in.AuctionCloseRunner.Trigger.TIMER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuctionCloseRunnerTest {

    private final AuctionFacade auctionFacade = mock(AuctionFacade.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AuctionCloseRunner runner = new AuctionCloseRunner(auctionFacade, meterRegistry);

    @Test
    @DisplayName("일괄 낙찰 → 일괄 유찰 → 남은 대상 건별 순서로 처리하고, 깨운 쪽을 센다")
    void runsInOrder() {
        when(auctionFacade.findCloseTargetIds(any(), anyInt())).thenReturn(List.of(7L));
        when(auctionFacade.closeAuction(anyLong(), any())).thenReturn(AuctionCloseResult.WON);

        runner.run(TIMER);

        InOrder order = inOrder(auctionFacade);
        order.verify(auctionFacade).winBidAuctions(any(), anyInt());
        order.verify(auctionFacade).failNoBidAuctions(any(), anyInt());
        order.verify(auctionFacade).findCloseTargetIds(any(), anyInt());
        order.verify(auctionFacade).closeAuction(org.mockito.ArgumentMatchers.eq(7L), any());
        assertThat(meterRegistry.get("auction.close.trigger").tag("source", "timer").counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("일괄 낙찰이 실패해도 일괄 유찰과 건별 처리는 계속한다")
    void continuesAfterBulkFailure() {
        when(auctionFacade.winBidAuctions(any(), anyInt())).thenThrow(new IllegalStateException("boom"));
        when(auctionFacade.findCloseTargetIds(any(), anyInt())).thenReturn(List.of());

        runner.run(POLL);

        verify(auctionFacade).failNoBidAuctions(any(), anyInt());
        verify(auctionFacade).findCloseTargetIds(any(), anyInt());
    }

    @Test
    @DisplayName("실행 중에 다른 쪽이 부르면 겹쳐 실행하지 않고, 지금 실행이 끝난 뒤 한 번 더 실행한다")
    void requestDuringRunIsRerunNotDropped() throws Exception {
        CountDownLatch inFirstRun = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(auctionFacade.winBidAuctions(any(), anyInt())).thenAnswer(inv -> {
            inFirstRun.countDown();
            release.await(2, TimeUnit.SECONDS);
            return 0;
        }).thenReturn(0);
        when(auctionFacade.findCloseTargetIds(any(), anyInt())).thenReturn(List.of());

        Thread first = new Thread(() -> runner.run(TIMER));
        first.start();
        assertThat(inFirstRun.await(2, TimeUnit.SECONDS)).isTrue();

        runner.run(POLL);   // 락을 못 잡고 바로 돌아온다 (겹쳐 실행하지 않음)
        release.countDown();
        first.join(2_000);

        verify(auctionFacade, atLeast(2)).winBidAuctions(any(), anyInt());   // 첫 실행 + 다시 실행
    }
}
