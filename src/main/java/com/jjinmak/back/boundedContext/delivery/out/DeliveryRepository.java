package com.jjinmak.back.boundedContext.delivery.out;

import com.jjinmak.back.boundedContext.delivery.domain.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    Optional<Delivery> findByOrderId(Long orderId);

    @Query("select d from Delivery d join fetch d.winner join fetch d.seller where d.orderId=:orderId")
    Optional<Delivery> findByOrderIdWithWinnerAndSeller(Long orderId);
}
