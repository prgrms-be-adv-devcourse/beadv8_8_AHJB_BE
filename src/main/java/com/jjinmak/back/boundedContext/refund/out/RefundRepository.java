package com.jjinmak.back.boundedContext.refund.out;

import com.jjinmak.back.boundedContext.refund.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    boolean existsByOrderId(Long orderId);
}
