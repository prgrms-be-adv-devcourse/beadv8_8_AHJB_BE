package com.jjinmak.payment.out.repository;

import com.jjinmak.payment.domain.ReconciliationMismatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationMismatchRepository extends JpaRepository<ReconciliationMismatch, Long> {
}
