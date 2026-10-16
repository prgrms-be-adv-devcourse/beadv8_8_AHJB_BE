package com.jjinmak.back.boundedContext.payment.out;

import com.jjinmak.back.boundedContext.payment.domain.Payment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Payment> findByOrderGroupId(Long orderGroupId);

    @EntityGraph(attributePaths = "items")
    Optional<Payment> findWithItemsById(Long id);
}
