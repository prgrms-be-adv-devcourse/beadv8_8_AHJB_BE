package com.jjinmak.back.boundedContext.payment.out;

import com.jjinmak.back.boundedContext.payment.domain.Payment;
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

    /** 내 결제 상세 (소유자 검증 포함) */
    @EntityGraph(attributePaths = "items")
    Optional<Payment> findWithItemsByIdAndHolderId(Long id, Long holderId);

    /** 내 결제 내역 (정렬은 Pageable 로). 목록에서도 주문 수를 보여주므로 items 를 함께 가져온다 */
    @EntityGraph(attributePaths = "items")
    Page<Payment> findByHolderId(Long holderId, Pageable pageable);
}
