package com.jjinmak.notification.out.push;

import com.jjinmak.notification.domain.Notification;
import com.jjinmak.notification.domain.NotificationCategory;
import com.jjinmak.notification.domain.NotificationTargetType;
import com.jjinmak.notification.domain.NotificationType;

public record PushMessage(
        Long notificationId,
        Long receiverId,
        NotificationType type,
        NotificationCategory category,
        String title,
        String body,
        NotificationTargetType targetType,
        Long targetId
) {
    public static PushMessage from(Notification notification) {
        return new PushMessage(
                notification.getId(),
                notification.getReceiverId(),
                notification.getType(),
                notification.getCategory(),
                notification.getTitle(),
                notification.getBody(),
                notification.getTargetType(),
                notification.getTargetId()
        );
    }
}