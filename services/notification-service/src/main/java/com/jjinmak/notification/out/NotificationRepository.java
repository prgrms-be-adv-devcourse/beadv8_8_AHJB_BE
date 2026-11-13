package com.jjinmak.notification.out;

import com.jjinmak.notification.domain.Notification;
import com.jjinmak.notification.domain.NotificationCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Limit;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import java.time.LocalDateTime;


public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query(nativeQuery = true, value = """
            insert into notification
                (receiver_id, type, category, title, body, target_type, target_id,
                 dedupe_key, event_occurred_at, created_at, updated_at)
            values
                (:#{#n.receiverId}, :#{#n.type.name()}, :#{#n.category.name()}, :#{#n.title}, :#{#n.body},
                 :#{#n.targetType.name()}, :#{#n.targetId}, :#{#n.dedupeKey}, :#{#n.eventOccurredAt},
                 :now, :now)
            on conflict (dedupe_key) do nothing
            returning id
            """)
    Optional<Long> insertIgnoringDuplicate(@Param("n") Notification notification, @Param("now") LocalDateTime now);

    @Query("""
        select n from Notification n
        where n.receiverId = :receiverId
          and (:category is null or n.category = :category)
          and (:cursor is null or n.id < :cursor)
        order by n.id desc
        """)
    List<Notification> findPage(@Param("receiverId") Long receiverId,
                                @Param("category") NotificationCategory category,
                                @Param("cursor") Long cursor,
                                Limit limit);

    long countByReceiverIdAndReadAtIsNull(Long receiverId);

    Optional<Notification> findByIdAndReceiverId(Long id, Long receiverId);

    @Modifying
    @Query("""
        update Notification n set n.readAt = :now, n.updatedAt = :now
        where n.receiverId = :receiverId and n.readAt is null
        """)
    int readAll(@Param("receiverId") Long receiverId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
        update Notification n set n.pushedAt = :now, n.updatedAt = :now
        where n.id in :ids and n.pushedAt is null
        """)
    int markPushed(@Param("ids") Collection<Long> ids, @Param("now") LocalDateTime now);

}