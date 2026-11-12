package com.jjinmak.notification.out;

import com.jjinmak.notification.domain.NotificationDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface NotificationDeviceRepository extends JpaRepository<NotificationDevice, Long> {

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into notification_device (member_id, token, platform, created_at, updated_at)
            values (:memberId, :token, :platform, :now, :now)
            on conflict (token) do update
                set member_id = excluded.member_id,
                    updated_at = excluded.updated_at
            """)
    int upsert(@Param("memberId") Long memberId,
               @Param("token") String token,
               @Param("platform") String platform,
               @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from NotificationDevice d where d.token = :token and d.memberId = :memberId")
    int deleteByTokenAndMemberId(@Param("token") String token, @Param("memberId") Long memberId);
}