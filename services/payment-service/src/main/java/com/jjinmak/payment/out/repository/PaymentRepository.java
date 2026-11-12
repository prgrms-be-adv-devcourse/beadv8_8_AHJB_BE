package com.jjinmak.payment.out.repository;

import com.jjinmak.payment.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Payment> findByOrderGroupId(Long orderGroupId);

    @EntityGraph(attributePaths = "items")
    Optional<Payment> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "items")
    Optional<Payment> findWithItemsByIdAndHolderId(Long id, Long holderId);

    @EntityGraph(attributePaths = "items")
    Page<Payment> findByHolderId(Long holderId, Pageable pageable);
}
