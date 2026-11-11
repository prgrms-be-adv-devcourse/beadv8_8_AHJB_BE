package com.jjinmak.notification.out;

import com.jjinmak.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into notification
                (receiver_id, type, category, title, body, target_type, target_id,
                 dedupe_key, event_occurred_at, created_at, updated_at)
            values
                (:#{#n.receiverId}, :#{#n.type.name()}, :#{#n.category.name()}, :#{#n.title}, :#{#n.body},
                 :#{#n.targetType.name()}, :#{#n.targetId}, :#{#n.dedupeKey}, :#{#n.eventOccurredAt},
                 :now, :now)
            on conflict (dedupe_key) do nothing
            """)
    int insertIgnoringDuplicate(@Param("n") Notification notification, @Param("now") LocalDateTime now);
}