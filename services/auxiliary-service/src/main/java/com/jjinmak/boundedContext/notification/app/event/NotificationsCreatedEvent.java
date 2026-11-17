package com.jjinmak.boundedContext.notification.app.event;

import java.util.List;

public record NotificationsCreatedEvent(List<Long> notificationIds) {}