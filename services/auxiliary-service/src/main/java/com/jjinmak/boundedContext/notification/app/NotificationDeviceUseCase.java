package com.jjinmak.boundedContext.notification.app;

import com.jjinmak.boundedContext.notification.domain.DevicePlatform;
import com.jjinmak.boundedContext.notification.out.NotificationDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationDeviceUseCase {

    private final NotificationDeviceRepository notificationDeviceRepository;

    public void register(Long memberId, String token, LocalDateTime now) {
        notificationDeviceRepository.upsert(memberId, token, DevicePlatform.WEB.name(), now);
    }

    public void unregister(Long memberId, String token) {
        notificationDeviceRepository.deleteByTokenAndMemberId(token, memberId);
    }
}