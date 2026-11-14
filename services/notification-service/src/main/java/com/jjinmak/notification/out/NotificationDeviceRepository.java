package com.jjinmak.notification.out;

import com.jjinmak.notification.domain.NotificationDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NotificationDeviceRepository extends JpaRepository<NotificationDevice, Long> {

    List<NotificationDevice> findByMemberIdIn(Collection<Long> memberIds);

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

    @Modifying
    @Query("delete from NotificationDevice d where d.token in :tokens")
    int deleteByTokenIn(@Param("tokens") Collection<String> tokens);
}