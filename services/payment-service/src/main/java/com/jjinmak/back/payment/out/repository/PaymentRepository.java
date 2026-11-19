package com.jjinmak.back.payment.out.repository;

import com.jjinmak.back.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 행 락을 얻으려고 기다리는 최대 시간(ms). 넘으면 PessimisticLockingFailureException 이 난다
    String LOCK_TIMEOUT_MS = "3000";

    @EntityGraph(attributePaths = "items")
    Optional<Payment> findByOrderGroupId(Long orderGroupId);

    @EntityGraph(attributePaths = "items")
    Optional<Payment> findWithItemsById(Long id);

    /**
     * Payment 를 SELECT ... FOR UPDATE 로 조회한다. 결제의 상태를 보고 바꾸는 흐름(결제 요청 · 승인 · 닫기)이 서로 끼어들지 못하게 한다.
     * 락은 트랜잭션이 끝날 때까지 유지된다. 승인은 토스 호출 동안 락을 쥐고 있으므로, 다른 흐름은 LOCK_TIMEOUT_MS 까지만 기다린다.
     * PostgreSQL 은 outer join 한 쪽을 잠글 수 없어 items 를 함께 읽지 않는다 (필요하면 같은 트랜잭션에서 지연 로딩된다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = LOCK_TIMEOUT_MS))
    @Query("select p from Payment p where p.orderGroupId = :orderGroupId")
    Optional<Payment> findByOrderGroupIdForUpdate(@Param("orderGroupId") Long orderGroupId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = LOCK_TIMEOUT_MS))
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "items")
    Optional<Payment> findWithItemsByIdAndHolderId(Long id, Long holderId);

    @EntityGraph(attributePaths = "items")
    Page<Payment> findByHolderId(Long holderId, Pageable pageable);
}
