package com.jjinmak.back.order.out.repository;

import com.jjinmak.back.order.domain.OrderOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderOutboxRepository extends JpaRepository<OrderOutbox, Long> {

    List<OrderOutbox> findTop100ByPublishedAtIsNullOrderByIdAsc();

    @Modifying
    @Query("update OrderOutbox o set o.publishedAt = :publishedAt, o.updatedAt = :publishedAt where o.id in :ids")
    void markPublished(List<Long> ids, LocalDateTime publishedAt);
}
