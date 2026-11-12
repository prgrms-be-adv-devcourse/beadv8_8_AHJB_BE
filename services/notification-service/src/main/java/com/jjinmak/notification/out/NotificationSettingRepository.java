package com.jjinmak.notification.out;

import com.jjinmak.notification.domain.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    List<NotificationSetting> findByMemberId(Long memberId);

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into notification_setting (member_id, category, push_enabled, created_at, updated_at)
            values (:memberId, :category, :pushEnabled, :now, :now)
            on conflict (member_id, category) do update
                set push_enabled = excluded.push_enabled,
                    updated_at = excluded.updated_at
            """)
    int upsert(@Param("memberId") Long memberId,
               @Param("category") String category,
               @Param("pushEnabled") boolean pushEnabled,
               @Param("now") LocalDateTime now);
}