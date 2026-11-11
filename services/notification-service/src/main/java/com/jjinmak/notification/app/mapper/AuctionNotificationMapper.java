package com.jjinmak.notification.app.mapper;

import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.notification.domain.Notification;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.jjinmak.notification.domain.NotificationTargetType.*;
import static com.jjinmak.notification.domain.NotificationType.*;

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

    private static final String NO_BID = "NO_BID";

    public List<Notification> fromFailed(AuctionFailedEvent event) {
        if (!NO_BID.equals(event.failureReason()) || event.sellerId() == null) {
            return List.of();
        }
        return List.of(
                Notification.create(event.sellerId(), AUCTION_NO_BID,
                        "경매가 유찰되었습니다", "입찰 없이 경매가 끝났어요. 상품을 다시 등록해 보세요.",
                        PRODUCT, event.productId(), "auction-" + event.auctionId(), event.failedAt())
        );
    }
}