package com.jjinmak.notification.app;

import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.notification.domain.Notification;
import com.jjinmak.notification.out.NotificationRepository;
import com.jjinmak.notification.support.NotificationIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static com.jjinmak.notification.domain.NotificationType.AUCTION_WON_BUYER;
import static com.jjinmak.notification.domain.NotificationType.AUCTION_WON_SELLER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class NotificationFacadeTest extends NotificationIntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 12, 0, 3);
    private static final AuctionWonEvent WON =
            new AuctionWonEvent(12L, 3L, 7L, 9L, 50_000L, 3_000L, NOW.minusSeconds(3));

    @Autowired NotificationFacade notificationFacade;
    @Autowired NotificationRepository notificationRepository;

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAllInBatch();
    }

    @Test
    void 낙찰_이벤트를_받으면_낙찰자와_판매자_알림_2건을_저장한다() {
        int saved = notificationFacade.notifyAuctionWon(WON, NOW);

        assertThat(saved).isEqualTo(2);
        assertThat(notificationRepository.findAll())
                .extracting(Notification::getReceiverId, Notification::getType)
                .containsExactlyInAnyOrder(
                        tuple(7L, AUCTION_WON_BUYER),
                        tuple(9L, AUCTION_WON_SELLER));
    }

    @Test
    void 같은_낙찰_이벤트를_다시_받아도_알림은_늘어나지_않는다() {
        notificationFacade.notifyAuctionWon(WON, NOW);

        int savedAgain = notificationFacade.notifyAuctionWon(WON, NOW.plusSeconds(10));

        assertThat(savedAgain).isZero();
        assertThat(notificationRepository.count()).isEqualTo(2);
    }

    @Test
    void 다른_경매의_낙찰은_별도_알림으로_저장한다() {
        notificationFacade.notifyAuctionWon(WON, NOW);
        AuctionWonEvent otherAuction = new AuctionWonEvent(13L, 4L, 7L, 9L, 60_000L, 3_000L, NOW);

        int saved = notificationFacade.notifyAuctionWon(otherAuction, NOW);

        assertThat(saved).isEqualTo(2);
        assertThat(notificationRepository.count()).isEqualTo(4);
    }
}
