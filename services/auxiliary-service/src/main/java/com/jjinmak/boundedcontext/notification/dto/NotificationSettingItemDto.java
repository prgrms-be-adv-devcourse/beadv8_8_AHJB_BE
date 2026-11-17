package com.jjinmak.boundedcontext.notification.dto;

import com.jjinmak.boundedcontext.notification.domain.NotificationCategory;

public record NotificationSettingItemDto(NotificationCategory category, boolean pushEnabled) {}