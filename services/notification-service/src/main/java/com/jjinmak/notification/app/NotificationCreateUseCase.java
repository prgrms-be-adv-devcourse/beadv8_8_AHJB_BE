package com.jjinmak.notification.app;

import com.jjinmak.notification.domain.Notification;
import com.jjinmak.notification.out.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationCreateUseCase {
    private final NotificationRepository notificationRepository;

    // 중복된 알림은 건너뛰고, 새로 저장된 건수를 돌려준다
    public int create(List<Notification> notifications, LocalDateTime now) {
        int saved = 0;
        for (Notification notification : notifications) {
            saved += notificationRepository.insertIgnoringDuplicate(notification, now);
        }
        return saved;
    }
}