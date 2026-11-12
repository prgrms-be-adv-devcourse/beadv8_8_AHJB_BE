package com.jjinmak.notification.dto;

import com.jjinmak.notification.domain.NotificationCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record NotificationSettingsUpdateRequestDto(
        @NotEmpty List<@Valid Item> settings
) {
    public record Item(
            @NotNull NotificationCategory category,
            @NotNull Boolean pushEnabled
    ) {}
}