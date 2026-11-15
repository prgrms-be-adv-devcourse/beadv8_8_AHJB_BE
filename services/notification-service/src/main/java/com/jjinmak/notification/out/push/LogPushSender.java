package com.jjinmak.notification.out.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "notification.push.fcm.enabled", havingValue = "false", matchIfMissing = true)
public class LogPushSender implements PushSender {

    @Override
    public List<PushResult> send(PushMessage message, List<String> tokens) {
        log.info("[푸시 로그 발송] notificationId={}, receiverId={}, type={}, title={}, devices={}",
                message.notificationId(), message.receiverId(), message.type(), message.title(), tokens.size());
        return tokens.stream().map(PushResult::success).toList();
    }
}