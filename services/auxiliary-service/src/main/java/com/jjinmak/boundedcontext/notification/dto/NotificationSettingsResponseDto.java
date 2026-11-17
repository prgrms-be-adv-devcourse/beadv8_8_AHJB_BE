package com.jjinmak.boundedcontext.notification.dto;

import java.util.List;

public record NotificationSettingsResponseDto(List<NotificationSettingItemDto> settings) {}