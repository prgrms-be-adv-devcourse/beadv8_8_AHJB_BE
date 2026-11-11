package com.jjinmak.notification.dto;

import java.util.List;

public record NotificationListResponseDto(List<NotificationItemDto> content, Long nextCursor, boolean hasNext) {}