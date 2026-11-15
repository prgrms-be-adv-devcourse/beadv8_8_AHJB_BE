package com.jjinmak.payment.out.repository;

import com.jjinmak.payment.domain.ReconciliationRun;
import com.jjinmak.payment.domain.enums.ReconciliationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {

    // 같은 종류·대상일의 대사 실행. 있으면 그 행을 다시 쓰고(재실행), 없으면 새로 시작한다
    Optional<ReconciliationRun> findByTypeAndTargetDate(ReconciliationType type, LocalDate targetDate);
}
