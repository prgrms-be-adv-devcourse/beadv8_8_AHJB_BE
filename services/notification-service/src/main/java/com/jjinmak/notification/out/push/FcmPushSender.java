package com.jjinmak.notification.out.push;

import com.google.firebase.messaging.*;
import com.jjinmak.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.push.fcm.enabled", havingValue = "true")
public class FcmPushSender implements PushSender {

    private final FirebaseMessaging firebaseMessaging;
    private final PushLinkResolver pushLinkResolver;

    @Override
    public List<PushResult> send(PushMessage message, List<String> tokens) {
        List<Message> messages = tokens.stream()
                .map(token -> toFcmMessage(message, token))
                .toList();

        BatchResponse batch;
        try {
            batch = firebaseMessaging.sendEach(messages);
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 요청 실패. notificationId={}, error={}", message.notificationId(), e.getMessagingErrorCode(), e);
            return tokens.stream().map(PushResult::failed).toList();
        }

        List<SendResponse> responses = batch.getResponses();
        return IntStream.range(0, tokens.size())
                .mapToObj(i -> toResult(message, tokens.get(i), responses.get(i)))
                .toList();
    }

    private Message toFcmMessage(PushMessage message, String token) {
        NotificationType type = message.type();
        String link = pushLinkResolver.resolve(message);

        WebpushNotification.Builder notification = WebpushNotification.builder()
                .setTitle(message.title())
                .setBody(message.body());
        if (type.getTagSuffix() != null) {
            notification.setTag("auction-" + message.targetId() + "-" + type.getTagSuffix())
                    .setRenotify(true);
        }

        WebpushConfig.Builder webpush = WebpushConfig.builder()
                .setNotification(notification.build())
                .setFcmOptions(WebpushFcmOptions.withLink(link))
                .putHeader("Urgency", type.isUrgent() ? "high" : "normal");
        if (type.getTtlSeconds() != null) {
            webpush.putHeader("TTL", String.valueOf(type.getTtlSeconds()));
        }

        return Message.builder()
                .setToken(token)
                .setWebpushConfig(webpush.build())
                .putData("notificationId", String.valueOf(message.notificationId()))
                .putData("type", type.name())
                .putData("link", link)
                .build();
    }

    private PushResult toResult(PushMessage message, String token, SendResponse response) {
        if (response.isSuccessful()) {
            return PushResult.success(token);
        }

        MessagingErrorCode code = response.getException().getMessagingErrorCode();
        if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.SENDER_ID_MISMATCH) {
            return PushResult.invalidToken(token);
        }

        log.warn("푸시 발송 실패. notificationId={}, type={}, token={}..., error={}",
                message.notificationId(), message.type(), token.substring(0, Math.min(10, token.length())), code);
        return PushResult.failed(token);
    }
}