package com.jjinmak.notification.app;

import com.jjinmak.notification.out.NotificationDeviceRepository;
import com.jjinmak.notification.out.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushWriter {

    private final NotificationRepository notificationRepository;
    private final NotificationDeviceRepository notificationDeviceRepository;

    @Transactional
    public void record(Collection<Long> pushedIds, Collection<String> invalidTokens, LocalDateTime now) {
        if (!pushedIds.isEmpty()) {
            notificationRepository.markPushed(pushedIds, now);
        }
        if (!invalidTokens.isEmpty()) {
            int deleted = notificationDeviceRepository.deleteByTokenIn(invalidTokens);
            log.info("무효 기기 토큰 삭제 {}건", deleted);
        }
    }
}