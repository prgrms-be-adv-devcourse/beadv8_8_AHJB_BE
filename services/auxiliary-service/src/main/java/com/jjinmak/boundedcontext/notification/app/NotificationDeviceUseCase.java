package com.jjinmak.boundedcontext.notification.app;

import com.jjinmak.boundedcontext.notification.domain.DevicePlatform;
import com.jjinmak.boundedcontext.notification.out.NotificationDeviceRepository;
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