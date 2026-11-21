package com.jjinmak.back.order.out.repository;

import com.jjinmak.back.order.domain.OrderGroup;
import com.jjinmak.back.order.domain.OrderMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderGroupRepository extends JpaRepository<OrderGroup, Long> {

    @Query("select og from OrderGroup og join fetch og.orders where og.id = :id")
    Optional<OrderGroup> findByIdWithOrders(Long id);

    @Query("select og from OrderGroup og join fetch og.orders where og.winner = :winner and og.lifeTime > :now and exists (select o.id from Order o where o.group = og and o.state = OrderState.WAITING)")
    List<OrderGroup> findWaitingByWinnerAndLifeTime(OrderMember winner, LocalDateTime now);
}
