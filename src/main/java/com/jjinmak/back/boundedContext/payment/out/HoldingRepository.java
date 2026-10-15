package com.jjinmak.back.boundedContext.payment.out;

import com.jjinmak.back.boundedContext.payment.domain.Holding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
    boolean existsByPaymentId(Long paymentId);

    Optional<Holding> findByOrderId(Long orderId);
}
