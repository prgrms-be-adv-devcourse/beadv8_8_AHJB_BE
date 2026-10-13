package com.jjinmak.back.boundedContext.order.out;

import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface OrderGroupRepository extends JpaRepository<OrderGroup, Long> {

    @Query("select og from OrderGroup og join fetch og.orders where og.id = :id")
    Optional<OrderGroup> findByIdWithOrders(Long id);
}
