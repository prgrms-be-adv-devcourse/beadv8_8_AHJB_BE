package com.jjinmak.notification.app.event;

import java.util.List;

public record NotificationsCreatedEvent(List<Long> notificationIds) {}