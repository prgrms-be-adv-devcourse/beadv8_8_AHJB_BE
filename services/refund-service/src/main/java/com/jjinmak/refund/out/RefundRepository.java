package com.jjinmak.refund.out;

import com.jjinmak.refund.domain.Refund;
import com.jjinmak.contracts.refund.domain.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    boolean existsByOrderId(Long orderId);

    Optional<Refund> findByOrderId(Long orderId);

    @Query("""
            select r.id from Refund r
            where r.status = :status and r.processedAt <= :before
            order by r.id
            """)
    List<Long> findRetryTargetIds(@Param("status") RefundStatus status, @Param("before") LocalDateTime before);
}