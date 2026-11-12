package com.jjinmak.notification.app;

import com.jjinmak.notification.out.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NotificationPushWriter {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void markPushed(List<Long> notificationIds, LocalDateTime now) {
        notificationRepository.markPushed(notificationIds, now);
    }
}