package com.jjinmak.boundedcontext.notification.app.event;

import java.util.List;

public record NotificationsCreatedEvent(List<Long> notificationIds) {}