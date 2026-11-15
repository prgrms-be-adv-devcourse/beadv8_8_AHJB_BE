package com.jjinmak.notification.in;

import com.jjinmak.notification.app.NotificationPushUseCase;
import com.jjinmak.notification.app.event.NotificationsCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.jjinmak.notification.config.NotificationPushConfig.PUSH_EXECUTOR;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushEventHandler {

    private final NotificationPushUseCase notificationPushUseCase;

    @Async(PUSH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationsCreatedEvent event) {
        try {
            notificationPushUseCase.push(event.notificationIds());
        } catch (Exception e) {
            log.error("푸시 발송 중 오류. notificationIds={}", event.notificationIds(), e);
        }
    }
}