package com.jjinmak.notification.app.mapper;

import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.notification.domain.Notification;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.jjinmak.notification.domain.NotificationCategory.BUY;
import static com.jjinmak.notification.domain.NotificationCategory.SELL;
import static com.jjinmak.notification.domain.NotificationTargetType.AUCTION;
import static com.jjinmak.notification.domain.NotificationTargetType.CART;
import static com.jjinmak.notification.domain.NotificationType.AUCTION_WON_BUYER;
import static com.jjinmak.notification.domain.NotificationType.AUCTION_WON_SELLER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuctionNotificationMapperTest {

    private static final LocalDateTime WIN_AT = LocalDateTime.of(2026, 11, 11, 12, 0);

    private final AuctionNotificationMapper mapper = new AuctionNotificationMapper();

    private AuctionWonEvent 낙찰(Long winnerId, Long sellerId) {
        return new AuctionWonEvent(12L, 3L, winnerId, sellerId, 50_000L, 3_000L, WIN_AT);
    }

    @Test
    void 낙찰_이벤트_한_건으로_낙찰자와_판매자_알림을_만든다() {
        List<Notification> notifications = mapper.fromWon(낙찰(7L, 9L));

        assertThat(notifications).hasSize(2);

        Notification buyer = notifications.get(0);
        assertThat(buyer.getReceiverId()).isEqualTo(7L);
        assertThat(buyer.getType()).isEqualTo(AUCTION_WON_BUYER);
        assertThat(buyer.getCategory()).isEqualTo(BUY);
        assertThat(buyer.getTargetType()).isEqualTo(CART);
        assertThat(buyer.getTargetId()).isEqualTo(12L);
        assertThat(buyer.getEventOccurredAt()).isEqualTo(WIN_AT);
        assertThat(buyer.getDedupeKey()).isEqualTo("AUCTION_WON_BUYER:auction-12:member-7");

        Notification seller = notifications.get(1);
        assertThat(seller.getReceiverId()).isEqualTo(9L);
        assertThat(seller.getType()).isEqualTo(AUCTION_WON_SELLER);
        assertThat(seller.getCategory()).isEqualTo(SELL);
        assertThat(seller.getTargetType()).isEqualTo(AUCTION);
        assertThat(seller.getTargetId()).isEqualTo(12L);
        assertThat(seller.getDedupeKey()).isEqualTo("AUCTION_WON_SELLER:auction-12:member-9");
    }

    @Test
    void 낙찰자_id가_없으면_재시도하지_않는_예외를_던진다() {
        assertThatThrownBy(() -> mapper.fromWon(낙찰(null, 9L)))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void 판매자_id가_없으면_재시도하지_않는_예외를_던진다() {
        assertThatThrownBy(() -> mapper.fromWon(낙찰(7L, null)))
                .isInstanceOf(InvalidEventException.class);
    }
}
