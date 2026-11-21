package com.jjinmak.boundedcontext.market.in;

import com.jjinmak.boundedcontext.market.app.MarketFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class MarketAuctionOpenSchedulerTest {

    @Mock MarketFacade marketFacade;

    @InjectMocks MarketAuctionOpenScheduler scheduler;

    private static List<Long> ids(long fromInclusive, long toInclusive) {
        return LongStream.rangeClosed(fromInclusive, toInclusive).boxed().toList();
    }

    @Test
    void 대상을_100건씩_묶어_한_트랜잭션씩_연다() {
        given(marketFacade.findAuctionOpenTargetIds(any())).willReturn(ids(1, 250));

        scheduler.openAuctions();

        then(marketFacade).should().openAuctions(eq(ids(1, 100)), any());
        then(marketFacade).should().openAuctions(eq(ids(101, 200)), any());
        then(marketFacade).should().openAuctions(eq(ids(201, 250)), any());
        then(marketFacade).should(never()).openAuction(any(), any());
    }

    @Test
    void 묶음이_실패하면_그_묶음만_건별로_다시_처리한다() {
        given(marketFacade.findAuctionOpenTargetIds(any())).willReturn(ids(1, 103));
        given(marketFacade.openAuctions(eq(ids(1, 100)), any())).willReturn(100);
        willThrow(new RuntimeException("버전 충돌")).given(marketFacade).openAuctions(eq(ids(101, 103)), any());

        scheduler.openAuctions();

        then(marketFacade).should(times(2)).openAuctions(anyList(), any());
        then(marketFacade).should().openAuction(eq(101L), any());
        then(marketFacade).should().openAuction(eq(102L), any());
        then(marketFacade).should().openAuction(eq(103L), any());
        then(marketFacade).should(never()).openAuction(eq(1L), any());
    }

    @Test
    void 건별_처리에서_하나가_실패해도_나머지는_연다() {
        given(marketFacade.findAuctionOpenTargetIds(any())).willReturn(ids(1, 2));
        willThrow(new RuntimeException("일괄 실패")).given(marketFacade).openAuctions(anyList(), any());
        willThrow(new RuntimeException("상품 1 실패")).given(marketFacade).openAuction(eq(1L), any());

        scheduler.openAuctions();

        then(marketFacade).should().openAuction(eq(2L), any());
    }
}
