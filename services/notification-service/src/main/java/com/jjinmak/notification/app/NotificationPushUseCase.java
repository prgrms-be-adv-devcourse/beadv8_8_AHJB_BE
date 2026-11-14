package com.jjinmak.notification.app;

import com.jjinmak.notification.out.push.PushResult;
import com.jjinmak.notification.out.push.PushSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        Set<String> invalidTokens = new LinkedHashSet<>();

        for (PushTarget target : targets) {
            List<PushResult> results = send(target);
            if (results.stream().anyMatch(PushResult::isSuccess)) {
                pushedIds.add(target.message().notificationId());
            }
            results.stream()
                    .filter(PushResult::isInvalidToken)
                    .map(PushResult::token)
                    .forEach(invalidTokens::add);
        }

        if (!pushedIds.isEmpty() || !invalidTokens.isEmpty()) {
            notificationPushWriter.record(pushedIds, invalidTokens, LocalDateTime.now());
        }
    }

    private List<PushResult> send(PushTarget target) {
        try {
            return pushSender.send(target.message(), target.tokens());
        } catch (Exception e) {
            log.warn("푸시 발송 실패. notificationId={}", target.message().notificationId(), e);
            return List.of();
        }
    }
}