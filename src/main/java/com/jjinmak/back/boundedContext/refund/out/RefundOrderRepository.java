package com.jjinmak.back.boundedContext.refund.out;

import com.jjinmak.back.boundedContext.refund.domain.RefundOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundOrderRepository extends JpaRepository<RefundOrder, Long> {
}
