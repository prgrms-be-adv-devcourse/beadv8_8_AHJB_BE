package com.jjinmak.payment.out.repository;

import com.jjinmak.payment.domain.ReconciliationMismatch;
import com.jjinmak.payment.domain.ReconciliationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationMismatchRepository extends JpaRepository<ReconciliationMismatch, Long> {

    // 이 실행에 달린 불일치 전부 (상태 무관). 재실행 때 이전에 기록한 건과 이번에 발견한 건을 비교하는 데 쓴다
    List<ReconciliationMismatch> findByRun(ReconciliationRun run);
}
