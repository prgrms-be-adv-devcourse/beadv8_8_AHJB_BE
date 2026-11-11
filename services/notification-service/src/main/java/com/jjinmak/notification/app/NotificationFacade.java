package com.jjinmak.notification.app;

import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.notification.app.mapper.AuctionNotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationFacade {

    private final AuctionNotificationMapper auctionNotificationMapper;
    private final NotificationCreateUseCase notificationCreateUseCase;

    @Transactional
    public int notifyAuctionWon(AuctionWonEvent event, LocalDateTime now) {
        return notificationCreateUseCase.create(auctionNotificationMapper.fromWon(event), now);
    }
}