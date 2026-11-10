package com.jjinmak.auction.app;

import com.jjinmak.auction.domain.DutchAuction;
import com.jjinmak.auction.domain.EnglishAuction;
import com.jjinmak.auction.out.eventPublisher.EventPublisher;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuctionEventPublisherTest {

    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 11, 20, 21, 0);

    @Mock EventPublisher eventPublisher;

    @InjectMocks AuctionEventPublisher auctionEventPublisher;

    private AuctionStartedEvent 발행된_시작_이벤트() {
        ArgumentCaptor<AuctionStartedEvent> captor = ArgumentCaptor.forClass(AuctionStartedEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        return captor.getValue();
    }

    @Test
    void 영국식_시작_이벤트에는_유형과_종료_시각이_담긴다() {
        EnglishAuction auction = EnglishAuction.create(1L, 100L, 10_000L, 50_000L, 3_000L, "CONSOLE",
                START_AT.plusDays(3), START_AT);

        auctionEventPublisher.publishStarted(auction);

        AuctionStartedEvent event = 발행된_시작_이벤트();
        assertThat(event.auctionType()).isEqualTo("ENGLISH");
        assertThat(event.endAt()).isEqualTo(START_AT.plusDays(3));
        assertThat(event.instantWinPrice()).isEqualTo(50_000L);
    }

    @Test
    void 네덜란드식_시작_이벤트에는_종료_시각과_즉시낙찰가가_없다() {
        DutchAuction auction = DutchAuction.create(1L, 100L, 300_000L, 150_000L, 2, 60, 3_000L, "CONSOLE", START_AT);

        auctionEventPublisher.publishStarted(auction);

        AuctionStartedEvent event = 발행된_시작_이벤트();
        assertThat(event.auctionType()).isEqualTo("DUTCH");
        assertThat(event.endAt()).isNull();
        assertThat(event.instantWinPrice()).isNull();
        assertThat(event.startAt()).isEqualTo(START_AT);
    }
}
