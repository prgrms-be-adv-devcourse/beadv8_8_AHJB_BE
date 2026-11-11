package com.jjinmak.notification.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.notification.domain.Notification;
import com.jjinmak.notification.out.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.notification.exception.NotificationErrorCode.NOTIFICATION_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class NotificationReadUseCase {

    private final NotificationRepository notificationRepository;

    public void read(Long memberId, Long notificationId, LocalDateTime now) {
        Notification notification = notificationRepository.findByIdAndReceiverId(notificationId, memberId)
                .orElseThrow(() -> new BusinessException(NOTIFICATION_NOT_FOUND));
        notification.read(now);
    }

    public int readAll(Long memberId, LocalDateTime now) {
        return notificationRepository.readAll(memberId, now);
    }
}