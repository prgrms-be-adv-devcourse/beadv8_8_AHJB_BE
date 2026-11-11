package com.jjinmak.notification.app;

import com.jjinmak.notification.domain.Notification;
import com.jjinmak.notification.domain.NotificationCategory;
import com.jjinmak.notification.dto.NotificationItemDto;
import com.jjinmak.notification.dto.NotificationListResponseDto;
import com.jjinmak.notification.out.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationQueryUseCase {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;

    private final NotificationRepository notificationRepository;

    public NotificationListResponseDto list(Long memberId, NotificationCategory category, Long cursor, Integer size) {
        int pageSize = normalize(size);
        List<Notification> found = notificationRepository.findPage(memberId, category, cursor, Limit.of(pageSize + 1));

        boolean hasNext = found.size() > pageSize;
        List<Notification> page = hasNext ? found.subList(0, pageSize) : found;
        Long nextCursor = hasNext ? page.getLast().getId() : null;

        return new NotificationListResponseDto(page.stream().map(NotificationItemDto::from).toList(), nextCursor, hasNext);
    }

    public long unreadCount(Long memberId) {
        return notificationRepository.countByReceiverIdAndReadAtIsNull(memberId);
    }

    private int normalize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}