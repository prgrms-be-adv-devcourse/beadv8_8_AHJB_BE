package com.jjinmak.notification.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 12, 0);

    private Notification 알림(NotificationType type, Long receiverId, String referenceKey) {
        return Notification.create(receiverId, type, "제목", "본문",
                NotificationTargetType.AUCTION, 12L, referenceKey, NOW);
    }

    @Test
    void 중복_방지_키는_종류_대상_받는_회원으로_만든다() {
        Notification notification = 알림(NotificationType.AUCTION_WON_BUYER, 7L, "auction-12");

        assertThat(notification.getDedupeKey()).isEqualTo("AUCTION_WON_BUYER:auction-12:member-7");
    }

    @Test
    void 묶음은_알림_종류가_정한다() {
        assertThat(알림(NotificationType.AUCTION_WON_BUYER, 7L, "auction-12").getCategory())
                .isEqualTo(NotificationCategory.BUY);
        assertThat(알림(NotificationType.AUCTION_WON_SELLER, 9L, "auction-12").getCategory())
                .isEqualTo(NotificationCategory.SELL);
        assertThat(알림(NotificationType.AUCTION_OUTBID, 3L, "bid-77").getCategory())
                .isEqualTo(NotificationCategory.BID);
    }

    @Test
    void 이미_읽은_알림을_다시_읽어도_처음_읽은_시각을_유지한다() {
        Notification notification = 알림(NotificationType.AUCTION_WON_BUYER, 7L, "auction-12");

        notification.read(NOW);
        notification.read(NOW.plusHours(1));

        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(NOW);
    }
}
