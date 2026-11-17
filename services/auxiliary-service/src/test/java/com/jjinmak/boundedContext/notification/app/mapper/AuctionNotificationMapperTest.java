package com.jjinmak.boundedContext.notification.app.mapper;

import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.auction.event.AuctionBidSucceedEvent;
import com.jjinmak.contracts.auction.event.AuctionEndingSoonEvent;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.boundedContext.notification.domain.Notification;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.jjinmak.boundedContext.notification.domain.NotificationCategory.BID;
import static com.jjinmak.boundedContext.notification.domain.NotificationCategory.BUY;
import static com.jjinmak.boundedContext.notification.domain.NotificationCategory.SELL;
import static com.jjinmak.boundedContext.notification.domain.NotificationTargetType.AUCTION;
import static com.jjinmak.boundedContext.notification.domain.NotificationTargetType.CART;
import static com.jjinmak.boundedContext.notification.domain.NotificationTargetType.PRODUCT;
import static com.jjinmak.boundedContext.notification.domain.NotificationType.AUCTION_ENDING_SOON;
import static com.jjinmak.boundedContext.notification.domain.NotificationType.AUCTION_NO_BID;
import static com.jjinmak.boundedContext.notification.domain.NotificationType.AUCTION_OUTBID;
import static com.jjinmak.boundedContext.notification.domain.NotificationType.AUCTION_WON_BUYER;
import static com.jjinmak.boundedContext.notification.domain.NotificationType.AUCTION_WON_SELLER;
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

    private AuctionFailedEvent 무산(String reason, Long sellerId) {
        return new AuctionFailedEvent(12L, 3L, reason, WIN_AT, sellerId);
    }

    @Test
    void 유찰이면_판매자에게_상품_상세로_가는_알림을_만든다() {
        List<Notification> notifications = mapper.fromFailed(무산("NO_BID", 9L));

        assertThat(notifications).hasSize(1);
        Notification seller = notifications.getFirst();
        assertThat(seller.getReceiverId()).isEqualTo(9L);
        assertThat(seller.getType()).isEqualTo(AUCTION_NO_BID);
        assertThat(seller.getCategory()).isEqualTo(SELL);
        assertThat(seller.getTargetType()).isEqualTo(PRODUCT);
        assertThat(seller.getTargetId()).isEqualTo(3L);
        assertThat(seller.getDedupeKey()).isEqualTo("AUCTION_NO_BID:auction-12:member-9");
    }

    @Test
    void 미결제나_환불로_인한_무산은_알림을_만들지_않는다() {
        assertThat(mapper.fromFailed(무산("UNPAID", 9L))).isEmpty();
        assertThat(mapper.fromFailed(무산("REFUNDED", 9L))).isEmpty();
    }

    @Test
    void 판매자_id가_없는_옛_무산_이벤트는_예외_없이_건너뛴다() {
        assertThat(mapper.fromFailed(무산("NO_BID", null))).isEmpty();
    }

    private static final LocalDateTime BID_AT = LocalDateTime.of(2026, 11, 15, 20, 0);

    private AuctionBidSucceedEvent 입찰(Long auctionId, Long bidderId, Long previousBidderId) {
        return new AuctionBidSucceedEvent(100L, auctionId, 3L, bidderId, 51_000L, 4,
                BID_AT.plusMinutes(30), BID_AT, previousBidderId);
    }

    private AuctionEndingSoonEvent 마감_임박(Long auctionId, List<Long> bidderIds) {
        return new AuctionEndingSoonEvent(auctionId, 3L, BID_AT.plusMinutes(10), bidderIds, BID_AT);
    }

    @Test
    void 상위_입찰이_들어오면_이전_최고_입찰자에게_알린다() {
        List<Notification> notifications = mapper.fromBidSucceed(입찰(12L, 7L, 5L));

        assertThat(notifications).hasSize(1);
        Notification outbid = notifications.get(0);
        assertThat(outbid.getReceiverId()).isEqualTo(5L);
        assertThat(outbid.getType()).isEqualTo(AUCTION_OUTBID);
        assertThat(outbid.getCategory()).isEqualTo(BID);
        assertThat(outbid.getTargetType()).isEqualTo(AUCTION);
        assertThat(outbid.getTargetId()).isEqualTo(12L);
        assertThat(outbid.getEventOccurredAt()).isEqualTo(BID_AT);
        assertThat(outbid.getDedupeKey()).isEqualTo("AUCTION_OUTBID:bid-100:member-5");
    }

    @Test
    void 첫_입찰이면_알리지_않는다() {
        assertThat(mapper.fromBidSucceed(입찰(12L, 7L, null))).isEmpty();
    }

    @Test
    void 최고_입찰자가_스스로_가격을_올리면_알리지_않는다() {
        assertThat(mapper.fromBidSucceed(입찰(12L, 7L, 7L))).isEmpty();
    }

    @Test
    void 상위_입찰인데_경매_id가_없으면_재시도하지_않는_예외를_던진다() {
        assertThatThrownBy(() -> mapper.fromBidSucceed(입찰(null, 7L, 5L)))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void 마감_임박이면_입찰자마다_한_건씩_알린다() {
        List<Notification> notifications = mapper.fromEndingSoon(마감_임박(12L, List.of(5L, 7L, 5L)));

        assertThat(notifications).extracting(Notification::getReceiverId).containsExactly(5L, 7L);
        assertThat(notifications).allSatisfy(n -> {
            assertThat(n.getType()).isEqualTo(AUCTION_ENDING_SOON);
            assertThat(n.getCategory()).isEqualTo(BID);
            assertThat(n.getTargetType()).isEqualTo(AUCTION);
            assertThat(n.getTargetId()).isEqualTo(12L);
            assertThat(n.getEventOccurredAt()).isEqualTo(BID_AT);
        });
        assertThat(notifications).extracting(Notification::getDedupeKey).containsExactly(
                "AUCTION_ENDING_SOON:auction-12:member-5",
                "AUCTION_ENDING_SOON:auction-12:member-7");
    }

    @Test
    void 마감_임박인데_입찰자가_없으면_알리지_않는다() {
        assertThat(mapper.fromEndingSoon(마감_임박(12L, List.of()))).isEmpty();
    }

    @Test
    void 마감_임박인데_경매_id가_없으면_재시도하지_않는_예외를_던진다() {
        assertThatThrownBy(() -> mapper.fromEndingSoon(마감_임박(null, List.of(5L))))
                .isInstanceOf(InvalidEventException.class);
    }
}
