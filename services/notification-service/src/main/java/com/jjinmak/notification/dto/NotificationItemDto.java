package com.jjinmak.notification.dto;

import com.jjinmak.notification.domain.*;

import java.time.LocalDateTime;

public record NotificationItemDto(
        Long notificationId,
        NotificationType type,
        NotificationCategory category,
        String title,
        String body,
        NotificationTargetType targetType,
        Long targetId,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationItemDto from(Notification n) {
        return new NotificationItemDto(n.getId(), n.getType(), n.getCategory(), n.getTitle(), n.getBody(),
                n.getTargetType(), n.getTargetId(), n.isRead(), n.getCreatedAt());
    }
}