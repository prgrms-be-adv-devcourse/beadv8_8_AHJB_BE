package com.jjinmak.notification.dto;

import com.jjinmak.notification.domain.NotificationCategory;

public record NotificationSettingItemDto(NotificationCategory category, boolean pushEnabled) {}