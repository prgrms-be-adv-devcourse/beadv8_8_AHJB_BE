package com.jjinmak.back.refund.out;

import com.jjinmak.back.refund.domain.RefundOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundOutboxRepository extends JpaRepository<RefundOutbox, Long> {

    List<RefundOutbox> findTop100ByPublishedAtIsNullOrderByIdAsc();
}