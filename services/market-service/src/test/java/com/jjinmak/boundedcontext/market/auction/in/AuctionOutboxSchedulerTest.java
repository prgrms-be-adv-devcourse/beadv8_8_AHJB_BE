package com.jjinmak.boundedcontext.market.auction.in;

import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import com.jjinmak.boundedcontext.market.auction.domain.AuctionOutbox;
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

class AuctionOutboxSchedulerTest {

    private final AuctionFacade auctionFacade = mock(AuctionFacade.class);
    private final AuctionOutboxScheduler scheduler = new AuctionOutboxScheduler(auctionFacade);

    private List<AuctionOutbox> 묶음(int size) {
        return IntStream.range(0, size).mapToObj(i -> new AuctionOutbox("auction.won", String.valueOf(i), "{}")).toList();
    }

    @Test
    @DisplayName("묶음을 보내고 보낸 행을 한 번에 발행 표시한다")
    void sendOutbox_sendsBatchAndMarks() {
        List<AuctionOutbox> batch = 묶음(3);
        when(auctionFacade.findUnpublishedOutboxes()).thenReturn(batch);
        when(auctionFacade.sendOutboxes(batch)).thenReturn(batch);

        scheduler.sendOutbox();

        verify(auctionFacade).markOutboxesPublished(batch);
        verify(auctionFacade, times(1)).findUnpublishedOutboxes();   // 묶음이 덜 찼으면 이번 실행 끝
    }

    @Test
    @DisplayName("묶음이 가득 차 있으면 1초 기다리지 않고 다음 묶음을 이어서 보낸다")
    void sendOutbox_continuesWhileBatchIsFull() {
        List<AuctionOutbox> full = 묶음(AuctionOutboxScheduler.BATCH_SIZE);
        List<AuctionOutbox> rest = 묶음(5);
        when(auctionFacade.findUnpublishedOutboxes()).thenReturn(full, rest);
        when(auctionFacade.sendOutboxes(full)).thenReturn(full);
        when(auctionFacade.sendOutboxes(rest)).thenReturn(rest);

        scheduler.sendOutbox();

        verify(auctionFacade).markOutboxesPublished(full);
        verify(auctionFacade).markOutboxesPublished(rest);
    }

    @Test
    @DisplayName("일부만 성공하면 성공한 앞부분만 표시하고 이번 실행을 멈춘다")
    void sendOutbox_stopsAfterPartialFailure() {
        List<AuctionOutbox> full = 묶음(AuctionOutboxScheduler.BATCH_SIZE);
        List<AuctionOutbox> head = full.subList(0, 10);
        when(auctionFacade.findUnpublishedOutboxes()).thenReturn(full);
        when(auctionFacade.sendOutboxes(full)).thenReturn(head);

        scheduler.sendOutbox();

        verify(auctionFacade).markOutboxesPublished(head);
        verify(auctionFacade, times(1)).findUnpublishedOutboxes();
    }

    @Test
    @DisplayName("보낼 행이 없으면 아무것도 하지 않는다")
    void sendOutbox_doesNothingWhenEmpty() {
        when(auctionFacade.findUnpublishedOutboxes()).thenReturn(Collections.emptyList());

        scheduler.sendOutbox();

        verify(auctionFacade, never()).sendOutboxes(anyList());
        verify(auctionFacade, never()).markOutboxesPublished(anyList());
    }
}
