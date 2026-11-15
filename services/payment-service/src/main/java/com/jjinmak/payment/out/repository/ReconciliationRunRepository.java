package com.jjinmak.payment.out.repository;

import com.jjinmak.payment.domain.ReconciliationRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {
}
