package com.jjinmak.back.boundedContext.review.out;

import com.jjinmak.back.boundedContext.review.domain.ReviewableOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReviewableOrderRepository extends JpaRepository<ReviewableOrder, Long> {

    boolean existsByOrderId(Long orderId);

    Optional<ReviewableOrder> findByOrderId(Long orderId);

    // 아직 후기를 쓰지 않았고 작성 기한이 남은 내 주문 (최근 구매확정순)
    @Query("""
            select ro from ReviewableOrder ro join fetch ro.seller
            where ro.buyer.id = :buyerId
              and ro.reviewed = false
              and ro.reviewDeadline > :now
            order by ro.confirmedAt desc
            """)
    List<ReviewableOrder> findReviewable(Long buyerId, LocalDateTime now);
}
