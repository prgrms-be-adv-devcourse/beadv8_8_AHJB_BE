package com.jjinmak.boundedContext.notification.dto;

import java.util.List;

public record NotificationSettingsResponseDto(List<NotificationSettingItemDto> settings) {}