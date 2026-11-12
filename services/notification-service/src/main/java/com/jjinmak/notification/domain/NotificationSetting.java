package com.jjinmak.notification.domain;

import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_notification_setting_member_category", columnNames = {"member_id", "category"}))
public class NotificationSetting extends BaseIdAndTime {

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private NotificationCategory category;

    @Column(nullable = false)
    private boolean pushEnabled;

    private NotificationSetting(Long memberId, NotificationCategory category, boolean pushEnabled) {
        this.memberId = memberId;
        this.category = category;
        this.pushEnabled = pushEnabled;
    }

    public static NotificationSetting create(Long memberId, NotificationCategory category, boolean pushEnabled) {
        return new NotificationSetting(memberId, category, pushEnabled);
    }
}