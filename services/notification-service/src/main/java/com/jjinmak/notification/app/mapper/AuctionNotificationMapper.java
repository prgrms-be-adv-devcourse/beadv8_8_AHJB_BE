package com.jjinmak.notification.app.mapper;

import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.notification.domain.Notification;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.jjinmak.notification.domain.NotificationTargetType.AUCTION;
import static com.jjinmak.notification.domain.NotificationTargetType.CART;
import static com.jjinmak.notification.domain.NotificationType.AUCTION_WON_BUYER;
import static com.jjinmak.notification.domain.NotificationType.AUCTION_WON_SELLER;

@Component
public class AuctionNotificationMapper {
    public List<Notification> fromWon(AuctionWonEvent event) {
        if (event.winnerId() == null || event.sellerId() == null) {
            throw new InvalidEventException("낙찰 이벤트에 받는 회원 id가 없습니다. auctionId=" + event.auctionId());
        }
        String referenceKey = "auction-" + event.auctionId();
        return List.of(
                Notification.create(event.winnerId(), AUCTION_WON_BUYER,
                        "낙찰되었습니다", "24시간 안에 결제해 주세요.",
                        CART, event.auctionId(), referenceKey, event.winAt()),
                Notification.create(event.sellerId(), AUCTION_WON_SELLER,
                        "상품이 낙찰되었습니다", "구매자의 결제를 기다리고 있어요.",
                        AUCTION, event.auctionId(), referenceKey, event.winAt())
        );
    }
}