package com.jjinmak.boundedcontext.delivery.out.repository;

import com.jjinmak.boundedcontext.delivery.domain.DeliveryOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryOutboxRepository extends JpaRepository<DeliveryOutbox, Long> {

    List<DeliveryOutbox> findTop100ByPublishedAtIsNullOrderByIdAsc();
}
