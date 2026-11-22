package com.jjinmak.boundedcontext.market.in;

import com.jjinmak.boundedcontext.market.app.MarketFacade;
import com.jjinmak.boundedcontext.market.shared.domain.MarketOutbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketOutboxSchedulerTest {

    private final MarketFacade marketFacade = mock(MarketFacade.class);
    private final MarketOutboxScheduler scheduler = new MarketOutboxScheduler(marketFacade);

    private List<MarketOutbox> 묶음(int size) {
        return IntStream.range(0, size).mapToObj(i -> new MarketOutbox("auction.won", String.valueOf(i), "{}")).toList();
    }

    @Test
    @DisplayName("묶음을 보내고 보낸 행을 한 번에 발행 표시한다")
    void sendOutbox_sendsBatchAndMarks() {
        List<MarketOutbox> batch = 묶음(3);
        when(marketFacade.findUnpublishedOutboxes()).thenReturn(batch);
        when(marketFacade.sendOutboxes(batch)).thenReturn(batch);

        scheduler.sendOutbox();

        verify(marketFacade).markOutboxesPublished(batch);
        verify(marketFacade, times(1)).findUnpublishedOutboxes();   // 묶음이 덜 찼으면 이번 실행 끝
    }

    @Test
    @DisplayName("묶음이 가득 차 있으면 1초 기다리지 않고 다음 묶음을 이어서 보낸다")
    void sendOutbox_continuesWhileBatchIsFull() {
        List<MarketOutbox> full = 묶음(MarketOutboxScheduler.BATCH_SIZE);
        List<MarketOutbox> rest = 묶음(5);
        when(marketFacade.findUnpublishedOutboxes()).thenReturn(full, rest);
        when(marketFacade.sendOutboxes(full)).thenReturn(full);
        when(marketFacade.sendOutboxes(rest)).thenReturn(rest);

        scheduler.sendOutbox();

        verify(marketFacade).markOutboxesPublished(full);
        verify(marketFacade).markOutboxesPublished(rest);
    }

    @Test
    @DisplayName("일부만 성공하면 성공한 앞부분만 표시하고 이번 실행을 멈춘다")
    void sendOutbox_stopsAfterPartialFailure() {
        List<MarketOutbox> full = 묶음(MarketOutboxScheduler.BATCH_SIZE);
        List<MarketOutbox> head = full.subList(0, 10);
        when(marketFacade.findUnpublishedOutboxes()).thenReturn(full);
        when(marketFacade.sendOutboxes(full)).thenReturn(head);

        scheduler.sendOutbox();

        verify(marketFacade).markOutboxesPublished(head);
        verify(marketFacade, times(1)).findUnpublishedOutboxes();
    }

    @Test
    @DisplayName("보낼 행이 없으면 아무것도 하지 않는다")
    void sendOutbox_doesNothingWhenEmpty() {
        when(marketFacade.findUnpublishedOutboxes()).thenReturn(Collections.emptyList());

        scheduler.sendOutbox();

        verify(marketFacade, never()).sendOutboxes(anyList());
        verify(marketFacade, never()).markOutboxesPublished(anyList());
    }
}
