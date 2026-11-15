package com.jjinmak.notification.app;

import com.jjinmak.notification.domain.Notification;
import com.jjinmak.notification.out.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationCreateUseCase {
    private final NotificationRepository notificationRepository;

    public List<Long> create(List<Notification> notifications, LocalDateTime now) {
        List<Long> savedIds = new ArrayList<>();
        for (Notification notification : notifications) {
            notificationRepository.insertIgnoringDuplicate(notification, now).ifPresent(savedIds::add);
        }
        return savedIds;
    }
}