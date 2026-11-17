package com.jjinmak.boundedContext.notification.dto;

import com.jjinmak.boundedContext.notification.domain.NotificationCategory;

public record NotificationSettingItemDto(NotificationCategory category, boolean pushEnabled) {}