package com.jjinmak.notification.in;

import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.notification.app.NotificationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_FAILED;
import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_WON;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationFacade notificationFacade;

    @KafkaListener(topics = AUCTION_WON, groupId = "notification")
    public void handleAuctionWon(AuctionWonEvent event) {
        int saved = notificationFacade.notifyAuctionWon(event, LocalDateTime.now());
        log.info("낙찰 알림 저장 auctionId={}, 저장={}", event.auctionId(), saved);
    }
    @KafkaListener(topics = AUCTION_FAILED, groupId = "notification")
    public void handleAuctionFailed(AuctionFailedEvent event) {
        int saved = notificationFacade.notifyAuctionFailed(event, LocalDateTime.now());
        log.info("유찰 알림 저장 auctionId={}, 사유={}, 저장={}", event.auctionId(), event.failureReason(), saved);
    }
}