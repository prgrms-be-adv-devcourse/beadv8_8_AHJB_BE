package com.jjinmak.notification.app;

import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.notification.app.mapper.AuctionNotificationMapper;
import com.jjinmak.notification.domain.NotificationCategory;
import com.jjinmak.notification.dto.NotificationListResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationFacade {

    private final AuctionNotificationMapper auctionNotificationMapper;
    private final NotificationCreateUseCase notificationCreateUseCase;
    private final NotificationQueryUseCase notificationQueryUseCase;
    private final NotificationReadUseCase notificationReadUseCase;

    @Transactional
    public int notifyAuctionWon(AuctionWonEvent event, LocalDateTime now) {
        return notificationCreateUseCase.create(auctionNotificationMapper.fromWon(event), now);
    }

    @Transactional(readOnly = true)
    public NotificationListResponseDto list(Long memberId, NotificationCategory category, Long cursor, Integer size) {
        return notificationQueryUseCase.list(memberId, category, cursor, size);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long memberId) {
        return notificationQueryUseCase.unreadCount(memberId);
    }

    @Transactional
    public void read(Long memberId, Long notificationId, LocalDateTime now) {
        notificationReadUseCase.read(memberId, notificationId, now);
    }

    @Transactional
    public int readAll(Long memberId, LocalDateTime now) {
        return notificationReadUseCase.readAll(memberId, now);
    }
}