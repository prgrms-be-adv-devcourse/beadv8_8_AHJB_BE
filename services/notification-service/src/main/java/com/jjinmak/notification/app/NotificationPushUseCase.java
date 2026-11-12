package com.jjinmak.notification.app;

import com.jjinmak.notification.out.push.PushResult;
import com.jjinmak.notification.out.push.PushSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPushUseCase {

    private final NotificationPushReader notificationPushReader;
    private final NotificationPushWriter notificationPushWriter;
    private final PushSender pushSender;

    public void push(List<Long> notificationIds) {
        List<PushTarget> targets = notificationPushReader.read(notificationIds);
        List<Long> pushedIds = new ArrayList<>();

        for (PushTarget target : targets) {
            if (send(target)) {
                pushedIds.add(target.message().notificationId());
            }
        }

        if (!pushedIds.isEmpty()) {
            notificationPushWriter.markPushed(pushedIds, LocalDateTime.now());
        }
    }

    private boolean send(PushTarget target) {
        try {
            List<PushResult> results = pushSender.send(target.message(), target.tokens());
            return results.stream().anyMatch(PushResult::isSuccess);
        } catch (Exception e) {
            log.warn("푸시 발송 실패. notificationId={}", target.message().notificationId(), e);
            return false;
        }
    }
}