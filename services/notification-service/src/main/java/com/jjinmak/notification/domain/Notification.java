package com.jjinmak.notification.domain;

import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_dedupe", columnNames = "dedupe_key"),
        indexes = {
                @Index(name = "idx_notification_receiver", columnList = "receiver_id, id DESC"),
                @Index(name = "idx_notification_receiver_category", columnList = "receiver_id, category, id DESC")
        })
public class Notification extends BaseIdAndTime {

    // 알림을 받는 회원 id
    @Column(nullable = false)
    private Long receiverId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private NotificationCategory category;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private NotificationTargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    // 같은 알림을 두 번 만들지 않기 위한 키: {종류}:{대상}:member-{받는 회원}
    @Column(nullable = false, length = 150)
    private String dedupeKey;

    private LocalDateTime eventOccurredAt;   // 원본 이벤트 발생 시각 (지연 측정)
    private LocalDateTime pushedAt;          // 푸시 발송 시각 (푸시 단계에서 사용)
    private LocalDateTime readAt;            // 읽은 시각 (null = 안 읽음)

    private Notification(Long receiverId, NotificationType type, String title, String body,
                         NotificationTargetType targetType, Long targetId, String dedupeKey,
                         LocalDateTime eventOccurredAt) {
        this.receiverId = receiverId;
        this.type = type;
        this.category = type.getCategory();
        this.title = title;
        this.body = body;
        this.targetType = targetType;
        this.targetId = targetId;
        this.dedupeKey = dedupeKey;
        this.eventOccurredAt = eventOccurredAt;
    }

    // referenceKey: 같은 종류 안에서 사건을 구분하는 값 (예: "auction-12", "order-3:DELIVERED", "bid-77")
    public static Notification create(Long receiverId, NotificationType type, String title, String body,
                                      NotificationTargetType targetType, Long targetId,
                                      String referenceKey, LocalDateTime eventOccurredAt) {
        String dedupeKey = type.name() + ":" + referenceKey + ":member-" + receiverId;
        return new Notification(receiverId, type, title, body, targetType, targetId, dedupeKey, eventOccurredAt);
    }

    // 이미 읽은 알림을 다시 읽어도 처음 읽은 시각을 유지한다
    public void read(LocalDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public boolean isRead() {
        return readAt != null;
    }
}