package com.jjinmak.back.boundedContext.settlement.out;

import com.jjinmak.back.boundedContext.settlement.domain.Settlement;
import com.jjinmak.back.shared.settlement.domain.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    boolean existsByOrderId(Long orderId);

    Page<Settlement> findBySellerId(Long sellerId, Pageable pageable);

    Page<Settlement> findBySellerIdAndStatus(Long sellerId, SettlementStatus status, Pageable pageable);

    @Query("""
            select s.id from Settlement s
            where s.status = :status and s.payoutDate <= :today
            order by s.id
            """)
    List<Long> findPayoutTargetIds(@Param("status") SettlementStatus status, @Param("today") LocalDate today);
}
