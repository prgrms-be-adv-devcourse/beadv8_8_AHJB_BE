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
@Table(
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_device_token", columnNames = "token"),
        indexes = @Index(name = "idx_notification_device_member", columnList = "member_id"))
public class NotificationDevice extends BaseIdAndTime {

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private DevicePlatform platform;

    private NotificationDevice(Long memberId, String token, DevicePlatform platform) {
        this.memberId = memberId;
        this.token = token;
        this.platform = platform;
    }

    public static NotificationDevice create(Long memberId, String token, DevicePlatform platform) {
        return new NotificationDevice(memberId, token, platform);
    }
}