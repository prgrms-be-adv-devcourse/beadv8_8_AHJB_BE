package com.jjinmak.back.settlement.out;

import com.jjinmak.back.settlement.domain.SettlementOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementOutboxRepository extends JpaRepository<SettlementOutbox, Long> {

    List<SettlementOutbox> findTop100ByPublishedAtIsNullOrderByIdAsc();
}