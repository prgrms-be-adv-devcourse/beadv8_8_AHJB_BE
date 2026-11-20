package com.jjinmak.boundedcontext.market.auction.in;

import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import com.jjinmak.boundedcontext.market.auction.domain.AuctionOutbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.concurrent.CompletionException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuctionOutboxSchedulerTest {

    private final AuctionFacade auctionFacade = mock(AuctionFacade.class);
    private final AuctionOutboxScheduler scheduler = new AuctionOutboxScheduler(auctionFacade);

    private AuctionOutbox outbox(long id) {
        AuctionOutbox outbox = spy(new AuctionOutbox("auction.won", String.valueOf(id), "{}"));
        when(outbox.getId()).thenReturn(id);
        return outbox;
    }

    @Test
    @DisplayName("저장 순서대로 보내고, 보낸 것마다 발행으로 표시한다")
    void sendOutbox_sendsInOrder() {
        AuctionOutbox first = outbox(1L);
        AuctionOutbox second = outbox(2L);
        when(auctionFacade.findUnpublishedOutboxes()).thenReturn(List.of(first, second));

        scheduler.sendOutbox();

        InOrder order = inOrder(auctionFacade);
        order.verify(auctionFacade).sendOutbox(first);
        order.verify(auctionFacade).markOutboxPublished(1L);
        order.verify(auctionFacade).sendOutbox(second);
        order.verify(auctionFacade).markOutboxPublished(2L);
    }

    @Test
    @DisplayName("전송이 실패하면 그 행은 표시하지 않고 회차를 멈춘다 (뒤 행도 보내지 않음)")
    void sendOutbox_stopsAtFirstFailure() {
        AuctionOutbox first = outbox(1L);
        AuctionOutbox second = outbox(2L);
        when(auctionFacade.findUnpublishedOutboxes()).thenReturn(List.of(first, second));
        doThrow(new CompletionException(new IllegalStateException("broker down"))).when(auctionFacade).sendOutbox(first);

        scheduler.sendOutbox();

        verify(auctionFacade, never()).markOutboxPublished(1L);
        verify(auctionFacade, never()).sendOutbox(second);
    }
}
