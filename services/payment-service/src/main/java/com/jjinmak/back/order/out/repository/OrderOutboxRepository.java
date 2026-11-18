package com.jjinmak.back.order.out.repository;

import com.jjinmak.back.order.domain.OrderOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderOutboxRepository extends JpaRepository<OrderOutbox, Long> {

    List<OrderOutbox> findTop100ByPublishedAtIsNullOrderByIdAsc();
}
